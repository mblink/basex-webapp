package org.basex.query.up.primitives;

import static org.basex.query.up.UpdateFunctions.*;
import org.basex.data.Data;
import org.basex.query.item.DBNode;
import org.basex.query.item.Nod;
import org.basex.query.iter.NodIter;

/**
 * Insert after primitive.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Lukas Kircher
 */
public final class InsertAfter extends NodeCopy {  
  /**
   * Constructor.
   * @param n target node
   * @param copy copy of nodes to be inserted
   */
  public InsertAfter(final Nod n, final NodIter copy) {
    super(n, copy);
  }

  @Override
  public void apply(final int add) {
    if(!(node instanceof DBNode)) return;
    
    // source nodes may be empty, thus insert has no effect at all
    if(m == null) return;
    final DBNode n = (DBNode) node;
    final int p = n.pre + add;
    final Data d = n.data;
    final int k = Nod.kind(node.type);
    // [LK] check if parent null?
    d.insert(p + d.size(p, k), d.parent(p, k), m);
    if(!mergeTextNodes(d, p - 1, p)) {
      final int s = m.meta.size;
      mergeTextNodes(d, p + s - 1, p + s);
    }
  }

  @Override
  public void merge(final UpdatePrimitive p) {
    c.add(((NodeCopy) p).c.getFirst());
  }

  @Override
  public PrimitiveType type() {
    return PrimitiveType.INSERTAFTER;
  }

  @Override
  public boolean addAtt() {
    return false;
  }
  
  @Override
  public boolean remAtt() {
    return false;
  }
}
