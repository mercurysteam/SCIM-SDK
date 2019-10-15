package de.gold.scim.endpoints;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.node.TextNode;

import de.gold.scim.constants.AttributeNames;
import de.gold.scim.constants.HttpHeader;
import de.gold.scim.constants.HttpStatus;
import de.gold.scim.constants.ScimType;
import de.gold.scim.endpoints.base.UserResourceType;
import de.gold.scim.exceptions.BadRequestException;
import de.gold.scim.exceptions.ConflictException;
import de.gold.scim.exceptions.InternalServerException;
import de.gold.scim.exceptions.ResourceNotFoundException;
import de.gold.scim.resources.User;
import de.gold.scim.resources.complex.Meta;
import de.gold.scim.response.CreateResponse;
import de.gold.scim.response.DeleteResponse;
import de.gold.scim.response.ErrorResponse;
import de.gold.scim.response.GetResponse;
import de.gold.scim.response.ScimResponse;
import de.gold.scim.response.UpdateResponse;
import de.gold.scim.utils.FileReferences;
import de.gold.scim.utils.JsonHelper;
import lombok.extern.slf4j.Slf4j;


/**
 * author Pascal Knueppel <br>
 * created at: 07.10.2019 - 23:54 <br>
 * <br>
 */
@Slf4j
public class ResourceEndpointHandlerTest implements FileReferences
{

  /**
   * the resource endpoints implementation that will handle any request
   */
  private ResourceEndpointHandler resourceEndpointHandler;

  /**
   * a mockito mock to verify that the methods are called correctly by the {@link ResourceEndpointHandler}
   * implementation
   */
  private TestUserHandlerImpl userHandler;

  /**
   * initializes this test
   */
  @BeforeEach
  public void initialize()
  {
    userHandler = Mockito.spy(new TestUserHandlerImpl());
    resourceEndpointHandler = new ResourceEndpointHandler(new UserResourceType(userHandler));
  }

  /**
   * this test must fail with an exception for no resource endpoints will be defined when creating the endpoint
   * handler
   */
  @Test
  public void testCreateEndpointWithoutResourceEndpoints()
  {
    Assertions.assertThrows(InternalServerException.class, ResourceEndpointHandler::new);
  }

  /**
   * this test will create, read, update, read, delete and read a user instance
   */
  @Test
  public void testLifeOfResource()
  {
    final String endpoint = "/Users";
    User createdUser = createUser(endpoint);
    String userId = createdUser.getId().orElse(null);
    User readUser = getUser(endpoint, userId);
    Assertions.assertEquals(createdUser, readUser);

    User updatedUser = updateUser(endpoint, readUser);
    Assertions.assertEquals(userId, updatedUser.getId().get());
    ScimResponse deleteResponse = resourceEndpointHandler.deleteResource(endpoint, userId);
    MatcherAssert.assertThat(deleteResponse.getClass(), Matchers.typeCompatibleWith(DeleteResponse.class));
    Mockito.verify(userHandler, Mockito.times(1)).deleteResource(userId);
    ScimResponse scimResponse = Assertions.assertDoesNotThrow(() -> resourceEndpointHandler.getResource(endpoint,
                                                                                                        userId));
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    MatcherAssert.assertThat(errorResponse.getScimException().getClass(),
                             Matchers.typeCompatibleWith(ResourceNotFoundException.class));
    Assertions.assertEquals(HttpStatus.SC_NOT_FOUND, errorResponse.getHttpStatus());
  }

  /**
   * creates a simple user and returns the created resource
   *
   * @param endpoint the resource endpoint that should be used
   * @return the created user
   */
  private User createUser(String endpoint)
  {
    ScimResponse scimResponse = resourceEndpointHandler.createResource(endpoint,
                                                                       readResourceFile(USER_RESOURCE),
                                                                       getBaseUrlSupplier());
    Mockito.verify(userHandler, Mockito.times(1)).createResource(Mockito.any());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(CreateResponse.class));
    Assertions.assertEquals(HttpStatus.SC_CREATED, scimResponse.getHttpStatus());
    String createResponse = scimResponse.toJsonDocument();
    Assertions.assertNotNull(createResponse);
    User user = JsonHelper.readJsonDocument(createResponse, User.class);
    String userId = user.getId().orElse(null);
    Assertions.assertNotNull(userId);
    Assertions.assertEquals(HttpHeader.SCIM_CONTENT_TYPE,
                            scimResponse.getHttpHeaders().get(HttpHeader.CONTENT_TYPE_HEADER));
    Assertions.assertNotNull(scimResponse.getHttpHeaders().get(HttpHeader.LOCATION_HEADER));
    Meta meta = user.getMeta().get();
    Assertions.assertEquals("User", meta.getResourceType().get());
    Assertions.assertEquals(getLocation(endpoint, userId), meta.getLocation().get());
    Assertions.assertTrue(meta.getCreated().isPresent());
    Assertions.assertTrue(meta.getLastModified().isPresent());
    // TODO check that the last modified value is correct
    return user;
  }

  /**
   * this test will throw an exception during object creation and check that the error response is correctly
   * returned
   */
  @Test
  public void testCreateUserWithScimException()
  {
    ConflictException exception = new ConflictException("blubb");
    Mockito.doThrow(exception).when(userHandler).createResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.createResource("/Users",
                                                                       readResourceFile(USER_RESOURCE),
                                                                       getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(exception, errorResponse.getScimException());
    Assertions.assertEquals(HttpStatus.SC_CONFLICT, errorResponse.getHttpStatus());
  }

  /**
   * this test will throw an exception during object creation and check that the error response is correctly
   * returned
   */
  @Test
  public void testCreateUserWithRuntimeException()
  {
    RuntimeException exception = new RuntimeException("blubb");
    Mockito.doThrow(exception).when(userHandler).createResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.createResource("/Users",
                                                                       readResourceFile(USER_RESOURCE),
                                                                       getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(InternalServerException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(exception, errorResponse.getScimException().getCause());
    Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorResponse.getHttpStatus());
  }

  /**
   * this test will try to get a resource with an empty id which should cause a
   * {@link ResourceNotFoundException}
   */
  @Test
  public void testGetResourceWithoutId()
  {
    ScimResponse scimResponse = resourceEndpointHandler.getResource("/Users", "", getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(ResourceNotFoundException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_NOT_FOUND, errorResponse.getHttpStatus());
  }

  /**
   * if the returned resource by the getResource endpoint has no id an {@link InternalServerException} must be
   * thrown
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "123456"})
  public void testGetResourceWithReturnedResourceHasDifferentId(String id)
  {
    User user = JsonHelper.loadJsonDocument(USER_RESOURCE, User.class);
    user.setId(null);
    Mockito.doReturn(user).when(userHandler).getResource(Mockito.eq(id));
    ScimResponse scimResponse = resourceEndpointHandler.getResource("/Users", id, getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(InternalServerException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorResponse.getHttpStatus());
  }

  /**
   * will show that a {@link de.gold.scim.exceptions.ScimException} is correctly handled by the
   * {@link ResourceEndpointHandler#getResource(String, String)} method
   */
  @Test
  public void testThrowScimExceptionOnGetResource()
  {
    ResourceNotFoundException exception = new ResourceNotFoundException("blubb", null, null);
    Mockito.doThrow(exception).when(userHandler).getResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.getResource("/Users", "123456", getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(ResourceNotFoundException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_NOT_FOUND, errorResponse.getHttpStatus());
  }

  /**
   * will show that a {@link RuntimeException} is correctly handled by the
   * {@link ResourceEndpointHandler#getResource(String, String)} method
   */
  @Test
  public void testThrowRuntimeExceptionOnGetResource()
  {
    RuntimeException exception = new RuntimeException("blubb");
    Mockito.doThrow(exception).when(userHandler).getResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.getResource("/Users", "123456", getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(InternalServerException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorResponse.getHttpStatus());
  }

  /**
   * if no resource is returned after update a {@link ResourceNotFoundException} should be thrown
   */
  @Test
  public void testSendUnusableResourceToUpdate()
  {
    User user = User.builder().id(UUID.randomUUID().toString()).build();
    ScimResponse scimResponse = resourceEndpointHandler.updateResource("/Users",
                                                                       UUID.randomUUID().toString(),
                                                                       user.toString(),
                                                                       getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(BadRequestException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, errorResponse.getHttpStatus());
    Assertions.assertEquals(ScimType.UNPARSABLE_REQUEST, errorResponse.getScimException().getScimType());
  }

  /**
   * if no resource is returned after update a {@link ResourceNotFoundException} should be thrown
   */
  @Test
  public void testDoNotReturnResourceAfterUpdate()
  {
    User user = JsonHelper.loadJsonDocument(USER_RESOURCE, User.class);
    Mockito.doReturn(null).when(userHandler).updateResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.updateResource("/Users",
                                                                       UUID.randomUUID().toString(),
                                                                       user.toString(),
                                                                       getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(ResourceNotFoundException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_NOT_FOUND, errorResponse.getHttpStatus());
  }

  /**
   * if the returned resource by the getResource endpoint has no id an {@link InternalServerException} must be
   * thrown
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "123456"})
  public void testUpdateResourceWithReturnedResourceHasDifferentId(String id)
  {
    User user = JsonHelper.loadJsonDocument(USER_RESOURCE, User.class);
    Mockito.doReturn(JsonHelper.copyResourceToObject(user.deepCopy(), User.class))
           .when(userHandler)
           .updateResource(Mockito.any());
    user.setId(null);
    ScimResponse scimResponse = resourceEndpointHandler.updateResource("/Users",
                                                                       id,
                                                                       user.toString(),
                                                                       getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(InternalServerException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorResponse.getHttpStatus());
  }

  /**
   * will show that a {@link de.gold.scim.exceptions.ScimException} is correctly handled by the
   * {@link ResourceEndpointHandler#updateResource(String, String, String)} method
   */
  @Test
  public void testThrowScimExceptionOnUpdateResource()
  {
    ResourceNotFoundException exception = new ResourceNotFoundException("blubb", null, null);
    Mockito.doThrow(exception).when(userHandler).updateResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.updateResource("/Users",
                                                                       "123456",
                                                                       readResourceFile(USER_RESOURCE));
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(ResourceNotFoundException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_NOT_FOUND, errorResponse.getHttpStatus());
  }

  /**
   * will show that a {@link RuntimeException} is correctly handled by the
   * {@link ResourceEndpointHandler#updateResource(String, String, String)} method
   */
  @Test
  public void testThrowRuntimeExceptionOnUpdateResource()
  {
    RuntimeException exception = new RuntimeException("blubb");
    Mockito.doThrow(exception).when(userHandler).updateResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.updateResource("/Users",
                                                                       "123456",
                                                                       readResourceFile(USER_RESOURCE));
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(InternalServerException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorResponse.getHttpStatus());
  }

  /**
   * will show that a {@link de.gold.scim.exceptions.ScimException} is correctly handled by the
   * {@link ResourceEndpointHandler#deleteResource(String, String)} method
   */
  @Test
  public void testThrowScimExceptionOnDeleteResource()
  {
    ResourceNotFoundException exception = new ResourceNotFoundException("blubb", null, null);
    Mockito.doThrow(exception).when(userHandler).deleteResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.deleteResource("/Users", "123456");
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(ResourceNotFoundException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_NOT_FOUND, errorResponse.getHttpStatus());
  }

  /**
   * will show that a {@link BadRequestException} is thrown if the parameters attributes and excludedAttributes
   * are set at the same time on creation request
   */
  @Test
  public void testThrowBadRequestIfAttributeAndExcludedAttribtesAreSetOnCreate()
  {
    ScimResponse scimResponse = resourceEndpointHandler.createResource("/Users",
                                                                       readResourceFile(USER_RESOURCE),
                                                                       "userName",
                                                                       "name",
                                                                       getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(BadRequestException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, errorResponse.getHttpStatus());
    Assertions.assertEquals(ScimType.INVALID_PARAMETERS, errorResponse.getScimException().getScimType());
  }

  /**
   * will show that a {@link BadRequestException} is thrown if the parameters attributes and excludedAttributes
   * are set at the same time on get request
   */
  @Test
  public void testThrowBadRequestIfAttributeAndExcludedAttribtesAreSetOnGet()
  {
    ScimResponse scimResponse = resourceEndpointHandler.getResource("/Users",
                                                                    "123456",
                                                                    "userName",
                                                                    "name",
                                                                    getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(BadRequestException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, errorResponse.getHttpStatus());
    Assertions.assertEquals(ScimType.INVALID_PARAMETERS, errorResponse.getScimException().getScimType());
  }

  /**
   * will show that a {@link BadRequestException} is thrown if the parameters attributes and excludedAttributes
   * are set at the same time on update request
   */
  @Test
  public void testThrowBadRequestIfAttributeAndExcludedAttribtesAreSetOnUpdate()
  {
    ScimResponse scimResponse = resourceEndpointHandler.updateResource("/Users",
                                                                       "123456",
                                                                       readResourceFile(USER_RESOURCE),
                                                                       "userName",
                                                                       "name",
                                                                       getBaseUrlSupplier());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(BadRequestException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, errorResponse.getHttpStatus());
    Assertions.assertEquals(ScimType.INVALID_PARAMETERS, errorResponse.getScimException().getScimType());
  }

  /**
   * will show that a {@link RuntimeException} is correctly handled by the
   * {@link ResourceEndpointHandler#deleteResource(String, String)} method
   */
  @Test
  public void testThrowRuntimeExceptionOnDeleteResource()
  {
    RuntimeException exception = new RuntimeException("blubb");
    Mockito.doThrow(exception).when(userHandler).deleteResource(Mockito.any());
    ScimResponse scimResponse = resourceEndpointHandler.deleteResource("/Users", "123456");
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(ErrorResponse.class));
    ErrorResponse errorResponse = (ErrorResponse)scimResponse;
    Assertions.assertEquals(InternalServerException.class, errorResponse.getScimException().getClass());
    Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorResponse.getHttpStatus());
  }

  /**
   * reads a user from the endpoint
   *
   * @param endpoint the resource endpoint that should be used
   * @param userId the id of the user that should be read
   * @return the returned user
   */
  private User getUser(String endpoint, String userId)
  {
    ScimResponse scimResponse = resourceEndpointHandler.getResource(endpoint, userId, getBaseUrlSupplier());
    Mockito.verify(userHandler, Mockito.times(1)).getResource(Mockito.eq(userId));
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(GetResponse.class));
    Assertions.assertEquals(HttpStatus.SC_OK, scimResponse.getHttpStatus());
    Assertions.assertEquals(HttpHeader.SCIM_CONTENT_TYPE,
                            scimResponse.getHttpHeaders().get(HttpHeader.CONTENT_TYPE_HEADER));
    Assertions.assertNotNull(scimResponse.getHttpHeaders().get(HttpHeader.LOCATION_HEADER));
    User user = JsonHelper.readJsonDocument(scimResponse.toJsonDocument(), User.class);
    Meta meta = user.getMeta().get();
    Assertions.assertEquals("User", meta.getResourceType().get());
    Assertions.assertEquals(getLocation(endpoint, userId), meta.getLocation().get());
    Assertions.assertTrue(meta.getCreated().isPresent());
    Assertions.assertTrue(meta.getLastModified().isPresent());
    // TODO check that the last modified value is correct
    return user;
  }

  /**
   * takes the given user updates some values and sends the updated user to the resource endpoint
   *
   * @param endpoint the resource endpoint that should be used
   * @param readUser the resource that will be copied and modified for the update
   * @return the updated user
   */
  private User updateUser(String endpoint, User readUser)
  {
    User updateUser = JsonHelper.copyResourceToObject(readUser.deepCopy(), User.class);
    final String usertype = "newUsertype";
    final String nickname = "newNickname";
    final String title = "newTitle";
    updateUser.setUserType(usertype);
    updateUser.setNickName(nickname);
    updateUser.setTitle(title);

    ScimResponse scimResponse = resourceEndpointHandler.updateResource(endpoint,
                                                                       readUser.getId().get(),
                                                                       updateUser.toString(),
                                                                       getBaseUrlSupplier());
    Mockito.verify(userHandler, Mockito.times(1)).updateResource(Mockito.any());
    MatcherAssert.assertThat(scimResponse.getClass(), Matchers.typeCompatibleWith(UpdateResponse.class));
    Assertions.assertEquals(HttpStatus.SC_OK, scimResponse.getHttpStatus());
    Assertions.assertEquals(HttpHeader.SCIM_CONTENT_TYPE,
                            scimResponse.getHttpHeaders().get(HttpHeader.CONTENT_TYPE_HEADER));
    Assertions.assertNotNull(scimResponse.getHttpHeaders().get(HttpHeader.LOCATION_HEADER));
    User updatedUser = JsonHelper.readJsonDocument(scimResponse.toJsonDocument(), User.class);
    Assertions.assertEquals(updateUser, updatedUser);
    Assertions.assertNotEquals(readUser, updatedUser);
    Assertions.assertEquals(usertype, updatedUser.getUserType().get());
    Assertions.assertEquals(nickname, updatedUser.getNickName().get());
    Assertions.assertEquals(title, updatedUser.getTitle().get());

    Meta meta = updatedUser.getMeta().get();
    Assertions.assertEquals("User", meta.getResourceType().get());
    Assertions.assertEquals(getLocation(endpoint, updatedUser.getId().get()), meta.getLocation().get());
    Assertions.assertTrue(meta.getCreated().isPresent());
    Assertions.assertTrue(meta.getLastModified().isPresent());
    // TODO check that the last modified value is correct
    return updatedUser;
  }

  /**
   * this method will get the current location uri to a resource
   *
   * @param resourceId the id of the resource
   * @return the full location URL of the endpoint
   */
  private String getLocation(String endpoint, String resourceId)
  {
    return getBaseUrlSupplier().get() + "/" + endpoint + "/" + resourceId;
  }

  /**
   * the base uri supplier that is given to the endpoint implementations
   */
  private Supplier<String> getBaseUrlSupplier()
  {
    return () -> "https://goldfish.de/scim/v2";
  }

  /**
   * a very simple test implementation for the users endpoint
   */
  private static class TestUserHandlerImpl extends ResourceHandler<User>
  {

    private Map<String, User> inMemoryMap = new HashMap<>();

    @Override
    public User createResource(User resource)
    {
      final String userId = UUID.randomUUID().toString();
      if (inMemoryMap.containsKey(userId))
      {
        throw new ConflictException("resource with id '" + userId + "' does already exist");
      }
      JsonHelper.addAttribute(resource, AttributeNames.ID, new TextNode(userId));
      inMemoryMap.put(userId, resource);
      resource.getMeta().ifPresent(meta -> {
        meta.setCreated(Instant.now());
        meta.setLastModified(Instant.now());
      });
      return resource;
    }

    @Override
    public User getResource(String id)
    {
      return inMemoryMap.get(id);
    }

    @Override
    public User listResources()
    {
      return null;
    }

    @Override
    public User updateResource(User resource)
    {
      String userId = resource.getId().get();
      User oldUser = getResource(userId);
      if (oldUser == null)
      {
        throw new ResourceNotFoundException("resource with id '" + userId + "' does not exist", null, null);
      }
      resource.getMeta().get().setCreated(oldUser.getMeta().get().getCreated().get());
      inMemoryMap.put(userId, resource);
      resource.getMeta().ifPresent(meta -> {
        meta.setLastModified(Instant.now());
      });
      return resource;
    }

    @Override
    public void deleteResource(String id)
    {
      if (inMemoryMap.containsKey(id))
      {
        inMemoryMap.remove(id);
      }
      else
      {
        throw new ResourceNotFoundException("resource with id '" + id + "' does not exist", null, null);
      }
    }
  }
}
