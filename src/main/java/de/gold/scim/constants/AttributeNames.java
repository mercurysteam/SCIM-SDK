package de.gold.scim.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;


/**
 * author Pascal Knueppel <br>
 * created at: 28.09.2019 - 15:09 <br>
 * <br>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttributeNames
{

  public static final String ID = "id";

  public static final String NAME = "name";

  public static final String DESCRIPTION = "description";

  public static final String ATTRIBUTES = "attributes";

  public static final String TYPE = "type";

  public static final String MULTI_VALUED = "multiValued";

  public static final String REQUIRED = "required";

  public static final String CASE_EXACT = "caseExact";

  public static final String MUTABILITY = "mutability";

  public static final String RETURNED = "returned";

  public static final String UNIQUENESS = "uniqueness";

  public static final String CANONICAL_VALUES = "canonicalValues";

  public static final String SUB_ATTRIBUTES = "subAttributes";
}
