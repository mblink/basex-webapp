package org.basex.query.up.primitives;

import org.basex.query.QueryException;
import org.basex.query.item.Nod;

/**
 * Abstract XQuery Update Primitive.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Lukas Kircher
 */
public abstract class UpdatePrimitive {
  /** Target node of update expression. */
  public final Nod node;
  
  /**
   * Constructor.
   * @param n DBNode reference
   */
  protected UpdatePrimitive(final Nod n) {
    node = n;
  }
  
  /**
   * Returns the type of the update primitive.
   * @return type
   */
  public abstract PrimitiveType type();
  
  /**
   * Checks for constraints, etc.
   * @throws QueryException query exception
   */
  public abstract void check() throws QueryException;
  
  /**
   * Applies the update operation represented by this primitive to the 
   * database. If an 'insert before' primitive is applied to a target node t,
   * the pre value of t changes. Thus the number of inserted nodes is added to
   * the pre value of t for all following update operations.
   * @param add size to add
   * @throws QueryException query exception 
   */
  public abstract void apply(final int add) throws QueryException;
  
  /**
   * Merges if possible two update primitives of the same type if they are
   * applied on the same target.
   * @param p primitive to be merged with 
   * @throws QueryException query exception
   */
  public abstract void merge(final UpdatePrimitive p) throws QueryException;
  
  /**
   * States if this update primitive adds an attribute to the attribute pool.
   * @return true if target node is an attribute
   */
  public abstract boolean addAtt();
  
  /**
   * States if this update primitive removes an attribute from the attribute 
   * pool.
   * @return true if target node is an attribute
   */
  public abstract boolean remAtt();
}
