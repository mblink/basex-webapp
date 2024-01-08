package org.basex.query.func.fn;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-24, BSD License
 * @author Christian Gruen
 */
public final class FnIterateWhile extends StandardFunc {
  @Override
  public Value value(final QueryContext qc) throws QueryException {
    final FItem predicate = toFunction(arg(1), 2, qc), action = toFunction(arg(2), 2, qc);
    Value value = arg(0).value(qc);

    int p = 0;
    while(true) {
      final Int pos = Int.get(++p);
      if(!toBoolean(qc, predicate, value, pos)) return value;
      value = action.invoke(qc, info, value, pos);
    }
  }

  @Override
  protected Expr opt(final CompileContext cc) throws QueryException {
    final Expr input = arg(0), predicate = arg(1), action = arg(2);

    // compute function types
    if(action instanceof FuncItem) {
      SeqType ist = input.seqType(), ost = SeqType.ITEM_ZM;
      Expr optAction = refineFunc(action, cc, ost, ist, SeqType.INTEGER_O);

      // repeat coercion until output types are equal and output type is instance of input type
      SeqType nst = optAction.funcType().declType;
      while(!ost.eq(nst) || !nst.instanceOf(ist)) {
        ist = ist.union(nst);
        optAction = refineFunc(action, cc, SeqType.ITEM_ZM, ist, SeqType.INTEGER_O);
        ost = nst;
        nst = nst.union(optAction.funcType().declType);
      }
      exprType.assign(ist.union(ost));
      final Expr oa = optAction;
      arg(2, arg -> oa);

      if(predicate instanceof FuncItem) {
        final SeqType is = ist;
        arg(1, arg -> refineFunc(predicate, cc, SeqType.BOOLEAN_O, is, SeqType.INTEGER_O));
        if(((FuncItem) predicate).expr == Bln.FALSE) return input;
      }
    }
    return this;
  }
}
