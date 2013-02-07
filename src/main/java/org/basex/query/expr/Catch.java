package org.basex.query.expr;

import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;

import org.basex.query.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Catch clause.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class Catch extends Single {
  /** Error QNames. */
  private static final QNm[] QNM = {
    create(ECODE), create(EDESC), create(EVALUE), create(EMODULE),
    create(ELINENUM), create(ECOLNUM), create(EADD)
  };
  /** Error types. */
  private static final SeqType[] TYPES = {
    SeqType.QNM, SeqType.STR_ZO, SeqType.ITEM_ZM, SeqType.STR_ZO,
    SeqType.ITR_ZO, SeqType.ITR_ZO, SeqType.ITEM_ZM
  };

  /** Error variables. */
  private final Var[] vars = new Var[QNM.length];
  /** Supported codes. */
  private final QNm[] codes;

  /**
   * Constructor.
   * @param ii input info
   * @param c supported error codes
   * @param ctx query context
   * @param scp variable scope
   */
  public Catch(final InputInfo ii, final QNm[] c, final QueryContext ctx,
      final VarScope scp) {
    super(ii, null);
    codes = c;
    for(int i = 0; i < QNM.length; i++)
      vars[i] = scp.newLocal(ctx, QNM[i], TYPES[i], false);
  }

  @Override
  public Catch compile(final QueryContext ctx, final VarScope scp) throws QueryException {
    super.compile(ctx, scp);
    type = expr.type();
    return this;
  }

  /**
   * Returns the value of the caught expression.
   * @param ctx query context
   * @param ex thrown exception
   * @return resulting item
   * @throws QueryException query exception
   */
  Value value(final QueryContext ctx, final QueryException ex) throws QueryException {
    if(!find(ex.err(), ex.qname())) return null;

    int i = 0;
    final byte[] io = ex.file() == null ? EMPTY : token(ex.file());
    final Value val = ex.value();
    for(final Value v : new Value[] { ex.qname(),
        Str.get(ex.getLocalizedMessage()), val == null ? Empty.SEQ : val,
        Str.get(io), Int.get(ex.line()), Int.get(ex.col()),
        Str.get(ex.getMessage().replaceAll("\r\n?", "\n")) }) {
      ctx.set(vars[i++], v, info);
    }
    return ctx.value(expr);
  }

  @Override
  public Expr copy(final QueryContext ctx, final VarScope scp, final IntMap<Var> vs) {
    final Catch ctch = new Catch(info, codes.clone(), ctx, scp);
    for(int i = 0; i < vars.length; i++) vs.add(vars[i].id, ctch.vars[i]);
    ctch.expr = expr.copy(ctx, scp, vs);
    return ctch;
  }

  /**
   * Returns the variables used in the {@code catch} expression.
   * @return variables
   */
  public Var[] vars() {
    return vars;
  }

  /**
   * Checks if one of the specified errors matches the thrown error.
   * @param err error reference
   * @param code error code
   * @return result of check
   */
  private boolean find(final Err err, final QNm code) {
    for(final QNm c : codes) {
      if(c != null) {
        final byte[] cu = c.uri();
        final byte[] eu = err != null ? err.qname().uri() :
          code.hasURI() ? code.uri() : EMPTY;
        if(cu.length != 0 && !eq(eu, cu)) continue;
        final byte[] nm = c.local();
        if(nm.length != 0 && !eq(code.local(), nm)) continue;
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean uses(final Use u) {
    return u == Use.VAR || super.uses(u);
  }

  @Override
  public String toString() {
    return "catch * { " + expr + " }";
  }

  /**
   * Creates an error QName with the specified name.
   * @param n name
   * @return QName
   */
  private static QNm create(final byte[] n) {
    return new QNm(concat(ERR, COLON, n), ERRORURI);
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    for(final Var v : vars) if(!visitor.declared(v)) return false;
    return visitAll(visitor, expr);
  }
}
