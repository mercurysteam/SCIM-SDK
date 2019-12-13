package de.captaingoldfish.scim.sdk.client.builder;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;

import com.fasterxml.jackson.databind.JsonNode;

import de.captaingoldfish.scim.sdk.client.exceptions.InvalidRequestException;
import de.captaingoldfish.scim.sdk.client.response.ScimServerResponse;
import de.captaingoldfish.scim.sdk.common.constants.HttpStatus;
import de.captaingoldfish.scim.sdk.common.resources.ResourceNode;
import de.captaingoldfish.scim.sdk.common.response.CreateResponse;
import de.captaingoldfish.scim.sdk.common.response.ErrorResponse;
import de.captaingoldfish.scim.sdk.common.response.ScimResponse;


/**
 * author Pascal Knueppel <br>
 * created at: 07.12.2019 - 23:13 <br>
 * <br>
 */
class CreateBuilder<T extends ResourceNode> extends RequestBuilder<T>
{

  public CreateBuilder(String baseUrl, ScimClientConfig scimClientConfig, Class<T> responseEntityType)
  {
    super(baseUrl, scimClientConfig, responseEntityType);
  }

  /**
   * @param endpoint the resource endpoint path e.g. /Users or /Groups
   */
  @Override
  public CreateBuilder<T> setEndpoint(String endpoint)
  {
    return (CreateBuilder<T>)super.setEndpoint(endpoint);
  }

  /**
   * @param resource sets the resource that should be sent to the service provider
   */
  @Override
  protected CreateBuilder<T> setResource(String resource)
  {
    return (CreateBuilder<T>)super.setResource(resource);
  }

  /**
   * @param resource sets the resource that should be sent to the service provider
   */
  @Override
  protected CreateBuilder<T> setResource(JsonNode resource)
  {
    return (CreateBuilder<T>)super.setResource(resource);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ScimServerResponse<T> sendRequest()
  {
    if (StringUtils.isBlank(getResource()))
    {
      throw new InvalidRequestException("no resource set");
    }
    return super.sendRequest();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected HttpUriRequest getHttpUriRequest()
  {
    HttpPost httpPost = new HttpPost(getBaseUrl() + getEndpoint());
    StringEntity stringEntity = new StringEntity(getResource(), StandardCharsets.UTF_8);
    httpPost.setEntity(stringEntity);
    return httpPost;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected <T extends ScimResponse> Class<T> getResponseType(int responseCode)
  {
    return responseCode == HttpStatus.CREATED ? (Class<T>)CreateResponse.class : (Class<T>)ErrorResponse.class;
  }
}
