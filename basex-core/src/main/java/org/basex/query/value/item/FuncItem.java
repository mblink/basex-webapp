package org.basex.query.value.item;

import static org.basex.query.QueryError.*;
import static org.basex.query.QueryText.*;

import java.util.*;
import java.util.function.*;

import org.basex.query.*;
import org.basex.query.ann.*;
import org.basex.query.expr.*;
import org.basex.query.expr.gflwor.*;
import org.basex.query.func.*;
import org.basex.query.scope.*;
import org.basex.query.util.*;
import org.basex.query.util.list.*;
import org.basex.query.value.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;
import org.basex.util.list.*;

/**
 * Function item.
 *
 * @author BaseX Team 2005-23, BSD License
 * @author Leo Woerteler
 */
public final class FuncItem extends FItem implements Scope {
  /** Static context. */
  public final StaticContext sc;
  /** Function expression. */
  private final Expr expr;
  /** Function name (can be {@code null}). */
  private final QNm name;
  /** Formal parameters. */
  private final Var[] params;
  /** Size of the stack frame needed for this function. */
  private final int stackSize;
  /** Input information. */
  private final InputInfo info;
  /** Query focus. */
  private final QueryFocus focus;
  /** Annotations (lazy instantiation). */
  private AnnList anns;
  /** Indicates if the query focus is accessed or modified. */
  private Boolean fcs;

  /**
   * Constructor.
   * @param sc static context
   * @param anns function annotations (can be {@code null})
   * @param name function name (can be {@code null})
   * @param params formal parameters
   * @param type function type
   * @param expr function body
   * @param stackSize stack-frame size
   * @param info input info
   */
  public FuncItem(final StaticContext sc, final AnnList anns, final QNm name, final Var[] params,
      final FuncType type, final Expr expr, final int stackSize, final InputInfo info) {
    this(sc, anns, name, params, type, expr, stackSize, info, null);
  }

  /**
   * Constructor.
   * @param sc static context
   * @param anns function annotations (can be {@code null})
   * @param name function name (can be {@code null})
   * @param params formal parameters
   * @param type function type
   * @param expr function body
   * @param stackSize stack-frame size
   * @param info input info
   * @param focus query focus (can be {@code null})
   */
  public FuncItem(final StaticContext sc, final AnnList anns, final QNm name, final Var[] params,
      final FuncType type, final Expr expr, final int stackSize, final InputInfo info,
      final QueryFocus focus) {

    super(type);
    this.name = name;
    this.params = params;
    this.expr = expr;
    this.stackSize = stackSize;
    this.sc = sc;
    this.info = info;
    this.focus = focus;
    this.anns = anns;
  }

  @Override
  public int arity() {
    return params.length;
  }

  @Override
  public QNm funcName() {
    return name;
  }

  @Override
  public QNm paramName(final int ps) {
    return params[ps].name;
  }

  @Override
  public AnnList annotations() {
    if(anns == null) anns = new AnnList();
    return anns;
  }

  @Override
  public Value invokeInternal(final QueryContext qc, final InputInfo ii, final Value[] args)
      throws QueryException {

    final int pl = params.length;
    for(int p = 0; p < pl; p++) qc.set(params[p], args[p]);

    // use shortcut if focus is not accessed
    if(fcs == null) fcs = expr.has(Flag.FCS, Flag.CTX, Flag.POS);
    if(!fcs) return expr.value(qc);

    final QueryFocus qf = qc.focus;
    qc.focus = focus != null ? focus : new QueryFocus();
    try {
      return expr.value(qc);
    } finally {
      qc.focus = qf;
    }
  }

  @Override
  public int stackFrameSize() {
    return stackSize;
  }

  @Override
  public FuncItem coerceTo(final FuncType ft, final QueryContext qc, final InputInfo ii,
      final boolean optimize) throws QueryException {

    final int arity = params.length, nargs = ft.argTypes.length;
    if(arity != nargs) throw arityError(this, arity, nargs, true, info);

    // optimize: continue with coercion if current type is only an instance of new type
    FuncType tp = funcType();
    if(optimize ? tp.eq(ft) : tp.instanceOf(ft)) return this;

    // create new compilation context and variable scope
    final CompileContext cc = new CompileContext(qc, false);
    final VarScope vs = new VarScope(sc);
    final Var[] vars = new Var[arity];
    final Expr[] args = new Expr[arity];
    for(int a = arity; a-- > 0;) {
      vars[a] = vs.addNew(params[a].name, ft.argTypes[a], true, qc, info);
      args[a] = new VarRef(info, vars[a]).optimize(cc);
    }
    cc.pushScope(vs);

    // create new function call (will immediately be inlined/simplified when being optimized)
    final boolean updating = anns != null && anns.contains(Annotation.UPDATING) ||
        expr.has(Flag.UPD);
    Expr body = new DynFuncCall(info, sc, updating, false, this, args);
    if(optimize) body = body.optimize(cc);

    // add type check if return types differ
    final SeqType dt = ft.declType;
    if(!tp.declType.instanceOf(dt)) {
      body = new TypeCheck(sc, info, body, dt, true);
      if(optimize) body = body.optimize(cc);
    }

    // adopt type of optimized body if it is more specific than passed on type
    final SeqType bt = body.seqType();
    tp = optimize && !bt.eq(dt) && bt.instanceOf(dt) ? FuncType.get(bt, ft.argTypes) : ft;
    body.markTailCalls(null);
    return new FuncItem(sc, anns, name, vars, tp, body, vs.stackSize(), info);
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    return visitor.funcItem(this);
  }

  @Override
  public boolean visit(final ASTVisitor visitor) {
    for(final Var param : params) {
      if(!visitor.declared(param)) return false;
    }
    return expr.accept(visitor);
  }

  @Override
  public boolean compiled() {
    return true;
  }

  @Override
  public Object toJava() {
    return this;
  }

  @Override
  public Expr inline(final Expr[] exprs, final CompileContext cc) throws QueryException {
    if(!StaticFunc.inline(cc, anns, expr) || expr.has(Flag.CTX)) return null;
    cc.info(OPTINLINE_X, this);

    // create let bindings for all variables
    final LinkedList<Clause> clauses = new LinkedList<>();
    final IntObjMap<Var> vm = new IntObjMap<>();
    final int pl = params.length;
    for(int p = 0; p < pl; p++) {
      clauses.add(new Let(cc.copy(params[p], vm), exprs[p]).optimize(cc));
    }

    // create the return clause
    final Expr rtrn = expr.copy(cc, vm).optimize(cc);
    rtrn.accept(new InlineVisitor());
    return clauses.isEmpty() ? rtrn : new GFLWOR(info, clauses, rtrn).optimize(cc);
  }

  @Override
  public Value atomValue(final QueryContext qc, final InputInfo ii) throws QueryException {
    throw FIATOMIZE_X.get(info, this);
  }

  @Override
  public Item atomItem(final QueryContext qc, final InputInfo ii) throws QueryException {
    throw FIATOMIZE_X.get(info, this);
  }

  @Override
  public byte[] string(final InputInfo ii) throws QueryException {
    throw FIATOMIZE_X.get(ii, this);
  }

  @Override
  public boolean deepEqual(final Item item, final DeepEqual deep) throws QueryException {
    if(deep.options.get(DeepEqualOptions.FALSE_ON_ERROR)) return false;
    throw FICOMPARE_X.get(info, this);
  }

  @Override
  public boolean vacuousBody() {
    final SeqType st = expr.seqType();
    return st != null && st.zero() && !expr.has(Flag.UPD);
  }

  @Override
  public boolean equals(final Object obj) {
    return this == obj;
  }

  @Override
  public String description() {
    return FUNCTION + ' ' + ITEM;
  }

  @Override
  public void toXml(final QueryPlan plan) {
    plan.add(plan.create(this, NAME, name == null ? null : name.prefixId()), params, expr);
  }

  @Override
  public String toErrorString() {
    final QueryString qs = new QueryString();
    if(name != null) {
      qs.concat(name.prefixId(), "#", arity());
    } else {
      final StringList list = new StringList(params.length);
      for(final Var param : params) list.add(param.toErrorString());
      if(anns != null) qs.token(anns);
      qs.token(FUNCTION).params(list.finish()).token(AS);
      qs.token(funcType().declType).brace(expr);
    }
    return qs.toString();
  }

  @Override
  public void toString(final QueryString qs) {
    if(name != null) qs.concat("(: ", name.prefixId(), "#", arity(), " :)");
    if(anns != null) qs.token(anns);
    qs.token(FUNCTION).params(params);
    qs.token(AS).token(funcType().declType).brace(expr);
  }

  /**
   * Optimizes the function item for a fold operation.
   * @param input input sequence
   * @param array indicates if an array is processed
   * @param left indicates if this is a left/right fold
   * @param cc compilation context
   * @return optimized expression or {@code null}
   * @throws QueryException query exception
   */
  public Object fold(final Expr input, final boolean array, final boolean left,
      final CompileContext cc) throws QueryException {
    if(arity() == 2 && !input.has(Flag.NDT)) {
      final Var actionVar = params[left ? 1 : 0], resultVar = params[left ? 0 : 1];
      final Predicate<Expr> result = ex -> ex instanceof VarRef &&
          ((VarRef) ex).var.equals(resultVar);

      // fold-left(SEQ, ZERO, f($result, $value) { VALUE })  ->  VALUE
      if(!array && input.seqType().oneOrMore() && expr instanceof Value) return expr;
      // fold-left(SEQ, ZERO, f($result, $value) { $result })  ->  $result
      if(result.test(expr)) return "";

      if(expr instanceof If) {
        final If iff = (If) expr;
        Expr cond = iff.cond, thn = iff.exprs[0], els = iff.exprs[1];
        if(!(cond.uses(actionVar) || cond.has(Flag.NDT))) {
          Expr cnd = cond, action = null;
          if(result.test(thn)) {
            // if(COND) then $result else ACTION
            // -> if COND($result): break; else $result = ACTION($result, $value)
            action = els;
          } else if(result.test(els)) {
            // if(COND) then ACTION else $result
            // -> if not(COND(result)): break; else $result = ACTION($result, $value)
            action = thn;
            cnd = cc.function(org.basex.query.func.Function.NOT, info, cond);
          }
          if(action != null) return new FuncItem[] {
            new FuncItem(sc, anns, null, params, funcType(), cnd, stackSize, info, focus),
            new FuncItem(sc, anns, null, params, funcType(), action, stackSize, info, focus)
          };
        }
      }
    }
    return null;
  }

  /**
   * A visitor for checking inlined expressions.
   *
   * @author BaseX Team 2005-23, BSD License
   * @author Leo Woerteler
   */
  private class InlineVisitor extends ASTVisitor {
    @Override
    public boolean inlineFunc(final Scope scope) {
      return scope.visit(this);
    }

    @Override
    public boolean dynFuncCall(final DynFuncCall call) {
      call.markInlined(FuncItem.this);
      return true;
    }
  }
}
