package org.basex.query.expr;

import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Simple map expression: iterative evaluation (no positional access).
 *
 * @author BaseX Team 2005-20, BSD License
 * @author Christian Gruen
 */
public final class IterMap extends SimpleMap {
  /**
   * Constructor.
   * @param info input info
   * @param exprs expressions
   */
  IterMap(final InputInfo info, final Expr... exprs) {
    super(info, exprs);
  }

  @Override
  public Iter iter(final QueryContext qc) {
    return new Iter() {
      final int sz = exprs.length;
      final QueryFocus focus = new QueryFocus();
      final Value[] values = new Value[sz];
      final Iter[] iter = new Iter[sz];
      int pos;

      @Override
      public Item next() throws QueryException {
        final QueryFocus qf = qc.focus;
        if(iter[0] == null) {
          iter[0] = exprs[0].iter(qc);
          values[0] = qf.value;
        }

        qc.focus = focus;
        try {
          do {
            focus.value = values[pos];
            final Item item = qc.next(iter[pos]);
            if(item == null) {
              if(--pos == -1) return null;
            } else if(pos < sz - 1) {
              focus.value = item;
              values[++pos] = item;
              iter[pos] = exprs[pos].iter(qc);
            } else {
              return item;
            }
          } while(true);
        } finally {
          qc.focus = qf;
        }
      }
    };
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    return iter(qc).value(qc, this);
  }

  @Override
  public IterMap copy(final CompileContext cc, final IntObjMap<Var> vm) {
    return copyType(new IterMap(info, Arr.copyAll(cc, vm, exprs)));
  }

  @Override
  public String description() {
    return "iterative " + super.description();
  }
}
