package org.basex.query.func;

import static org.basex.query.QueryError.*;
import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;

import java.lang.reflect.*;
import java.util.*;

import org.basex.query.*;
import org.basex.query.ann.*;
import org.basex.query.expr.*;
import org.basex.query.func.java.*;
import org.basex.query.util.*;
import org.basex.query.util.hash.*;
import org.basex.query.util.list.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.value.type.Type;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;
import org.basex.util.list.*;
import org.basex.util.similarity.*;

/**
 * This class provides access to built-in and user-defined functions.
 *
 * @author BaseX Team 2005-23, BSD License
 * @author Christian Gruen
 */
public final class Functions {
  /** Signatures of built-in functions. */
  public static final ArrayList<FuncDefinition> DEFINITIONS = new ArrayList<>();

  /** Cached functions. */
  private static final TokenObjMap<QNm> CACHE = new TokenObjMap<>();
  /** URIs of built-in functions. */
  private static final TokenSet URIS = new TokenSet();
  /** Cast parameter. */
  private static final QNm[] CAST_PARAM = { new QNm("value") };

  /** Private constructor. */
  private Functions() { }

  // initializes built-in XQuery functions
  static {
    // add built-in core functions
    Function.init(DEFINITIONS);
    // add built-in API functions if available
    final Class<?> clz = Reflect.find("org.basex.query.func.ApiFunction");
    final Method mth = Reflect.method(clz, "init", ArrayList.class);
    if(mth != null) Reflect.invoke(mth, null, DEFINITIONS);

    for(final FuncDefinition fd : DEFINITIONS) {
      URIS.add(fd.uri);
      final QNm qnm = new QNm(fd.local(), fd.uri());
      CACHE.put(qnm.internal(), qnm);
    }
  }

  /**
   * Checks if the specified URI is statically available.
   * @param uri URI to check
   * @return result of check
   */
  public static boolean staticURI(final byte[] uri) {
    for(final byte[] u : URIS) {
      if(eq(uri, u)) return true;
    }
    return false;
  }

  /**
   * Returns a function call for a function with the specified name and arguments.
   * @param name name of the function
   * @param args positional arguments
   * @param keywords keyword arguments (can be {@code null})
   * @param qc query context
   * @param sc static context
   * @param ii input info
   * @return function call
   * @throws QueryException query exception
   */
  public static Expr get(final QNm name, final Expr[] args, final QNmMap<Expr> keywords,
      final QueryContext qc, final StaticContext sc, final InputInfo ii) throws QueryException {

    // constructor function
    if(eq(name.uri(), XS_URI)) {
      return constructor(name, args, keywords, sc, ii);
    }

    // built-in function
    final FuncDefinition fd = builtIn(name);
    if(fd != null) {
      final int min = fd.minMax[0], max = fd.minMax[1];
      final Expr[] prepared = prepareArgs(args, keywords, fd.names, min, max, fd, ii);
      final StandardFunc sf = fd.get(sc, ii, prepared);
      if(sf != null) {
        if(sf.updating()) qc.updating();
        return sf;
      }
    }

    // user-defined function
    return userDefined(name, args, keywords, qc, sc, ii);
  }

  /**
   * Returns a function literal for a function with the specified name and arguments.
   * @param name function name
   * @param arity number of arguments
   * @param qc query context
   * @param sc static context
   * @param ii input info
   * @param runtime {@code true} if this method is called at runtime
   * @return function literal if found, {@code null} otherwise
   * @throws QueryException query exception
   */
  public static Expr literal(final QNm name, final int arity, final QueryContext qc,
      final StaticContext sc, final InputInfo ii, final boolean runtime) throws QueryException {

    final Literal lit = new Literal(sc, arity);

    // constructor function
    if(eq(name.uri(), XS_URI)) {
      if(arity > 0) lit.add(CAST_PARAM[0], SeqType.ANY_ATOMIC_TYPE_ZO, qc, ii);
      final Expr expr = constructor(name, lit.args, null, sc, ii);
      final FuncType ft = FuncType.get(lit.annotations(), null, lit.params);
      return literal(ii, expr, ft, name, lit, runtime, false, arity == 0);
    }

    // built-in function
    final FuncDefinition fd = builtIn(name);
    if(fd != null) {
      checkArity(arity, fd.minMax[0], fd.minMax[1], fd, ii, true);

      final FuncType ft = fd.type(arity, lit.annotations());
      final QNm[] names = fd.paramNames(arity);
      for(int a = 0; a < arity; a++) lit.add(names[a], ft.argTypes[a], qc, ii);
      final StandardFunc sf = fd.get(sc, ii, lit.args);
      final boolean updating = sf.updating(), ctx = sf.has(Flag.CTX);
      if(updating) {
        lit.annotations().add(new Ann(ii, Annotation.UPDATING, Empty.VALUE));
        qc.updating();
      }
      return literal(ii, sf, ft, name, lit, runtime, updating, ctx);
    }

    // user-defined function
    final StaticFunc sf = qc.functions.get(name, arity);
    if(sf != null) {
      final Expr func = userDefined(sf, qc, sc, ii, runtime, lit);
      if(sf.updating) qc.updating();
      return func;
    }

    for(int a = 0; a < arity; a++) lit.add(new QNm(ARG + (a + 1), ""), null, qc, ii);

    // Java function
    final JavaCall java = JavaCall.get(name, lit.args, qc, sc, ii);
    if(java != null) {
      final SeqType[] sts = new SeqType[arity];
      Arrays.fill(sts, SeqType.ITEM_ZM);
      final SeqType st = FuncType.get(lit.annotations(), null, sts).seqType();
      return new FuncLit(ii, java, st, name, lit.params, lit.annotations(), lit.vs);
    }
    if(runtime) return null;

    // literal
    final StaticFuncCall call = userDefined(name, lit.args, null, qc, sc, ii);
    // safe cast (no context dependency, no runtime evaluation)
    final Closure closure = (Closure) literal(ii, call, null, name, lit, runtime, false, false);
    qc.functions.register(closure);
    return closure;
  }

  /**
   * Creates a function item for a user-defined function.
   * @param sf static function
   * @param qc query context
   * @param sc static context
   * @param ii input info
   * @return function item
   * @throws QueryException query exception
   */
  public static FuncItem userDefined(final StaticFunc sf, final QueryContext qc,
      final StaticContext sc, final InputInfo ii) throws QueryException {
    // safe cast (no context dependency, runtime evaluation)
    return (FuncItem) userDefined(sf, qc, sc, ii, true, new Literal(sc, sf.arity()));
  }

  /**
   * Raises an error for the wrong number of function arguments.
   * @param nargs number of supplied arguments
   * @param arities available arities (if first arity is negative, function is variadic)
   * @param function function
   * @param ii input info
   * @param literal literal
   * @return error
   */
  public static QueryException wrongArity(final int nargs, final IntList arities,
      final Object function, final InputInfo ii, final boolean literal) {

    final String supplied = literal ? "Arity " + nargs : arguments(nargs), expected;
    if(!arities.isEmpty() && arities.peek() < 0) {
      expected = "at least " + -arities.peek();
    } else {
      final int as = arities.ddo().size();
      int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
      for(int a = 0; a < as; a++) {
        final int m = arities.get(a);
        if(m < min) min = m;
        if(m > max) max = m;
      }
      final TokenBuilder tb = new TokenBuilder();
      if(as > 2 && max - min + 1 == as) {
        tb.addInt(min).add('-').addInt(max);
      } else {
        for(int a = 0; a < as; a++) {
          if(a != 0) tb.add(a + 1 < as ? ", " : " or ");
          tb.addInt(arities.get(a));
        }
      }
      expected = tb.toString();
    }
    return INVNARGS_X_X_X.get(ii, function, supplied, expected);
  }

  /**
   * Returns the definition of a built-in function with the specified name.
   * @param name function name
   * @return function definition if found, {@code null} otherwise
   */
  static FuncDefinition builtIn(final QNm name) {
    final int id = CACHE.id(name.internal());
    return id != 0 ? DEFINITIONS.get(id - 1) : null;
  }
  /**
   * Returns an info message for a similar function.
   * @param qname name of type
   * @return info string
   */
  static byte[] similar(final QNm qname) {
    // find similar function in several attempts
    final ArrayList<QNm> qnames = new ArrayList<>(CACHE.size());
    for(final QNm qnm : CACHE.values()) qnames.add(qnm);
    final byte[] local = lc(qname.local()), uri = qname.uri();

    // find functions with identical URIs and similar local names
    Object similar = Levenshtein.similar(qname.local(), qnames.toArray(),
        o -> eq(uri, ((QNm) o).uri()) ? ((QNm) o).local() : null);
    // find functions with identical local names
    for(final QNm qnm : qnames) {
      if(similar == null && eq(lc(qnm.local()), local)) similar = qnm;
    }
    // find functions with identical URIs and local names that start with the specified name
    for(final QNm qnm : qnames) {
      if(similar == null && eq(uri, qnm.uri()) && startsWith(lc(qnm.local()), local)) similar = qnm;
    }
    return QueryError.similar(qname.prefixString(),
        similar != null ? ((QNm) similar).prefixString() : null);
  }

  /**
   * Incorporates keywords in the argument list.
   * @param args positional arguments
   * @param keywords keyword arguments
   * @param names parameter names
   * @param function function
   * @param ii input info
   * @return arguments
   * @throws QueryException query exception
   */
  static Expr[] prepareArgs(final Expr[] args, final QNmMap<Expr> keywords, final QNm[] names,
      final Object function, final InputInfo ii) throws QueryException {

    final ExprList list = new ExprList().add(args);
    final int nl = names.length;
    for(final QNm qnm : keywords) {
      int n = nl;
      while(--n >= 0 && !qnm.eq(names[n]));
      if(n == -1) throw KEYWORDUNKNOWN_X_X.get(ii, function, qnm);
      if(list.get(n) != null) throw ARGTWICE_X_X.get(ii, function, qnm);
      list.set(n, keywords.get(qnm));
    }
    return list.finish();
  }

  /**
   * Tries to resolve the specified function with xs namespace as a cast.
   * @param name function name
   * @param args positional arguments
   * @param keywords keyword arguments (can be {@code null})
   * @param sc static context
   * @param ii input info
   * @return cast type if found, {@code null} otherwise
   * @throws QueryException query exception
   */
  private static Cast constructor(final QNm name, final Expr[] args, final QNmMap<Expr> keywords,
      final StaticContext sc, final InputInfo ii) throws QueryException {

    Type type = ListType.find(name);
    if(type == null) type = AtomType.find(name, false);
    if(type == null) throw WHICHFUNC_X.get(ii, AtomType.similar(name));
    if(type.oneOf(AtomType.NOTATION, AtomType.ANY_ATOMIC_TYPE))
      throw ABSTRACTFUNC_X.get(ii, name.prefixId());

    final Expr[] prepared = prepareArgs(args, keywords, CAST_PARAM, 0, 1, name.string(), ii);
    return new Cast(sc, ii, prepared.length != 0 ? prepared[0] :
      new ContextValue(ii), SeqType.get(type, Occ.ZERO_OR_ONE));
  }

  /**
   * Returns a cached function call.
   * @param name function name
   * @param args positional arguments
   * @param keywords keyword arguments (can be {@code null})
   * @param qc query context
   * @param sc static context
   * @param ii input info
   * @return function call
   * @throws QueryException query exception
   */
  private static StaticFuncCall userDefined(final QNm name, final Expr[] args,
      final QNmMap<Expr> keywords, final QueryContext qc, final StaticContext sc,
      final InputInfo ii) throws QueryException {

    if(NSGlobal.reserved(name.uri())) throw qc.functions.similarError(name, ii);

    final StaticFuncCall call = new StaticFuncCall(name, args, keywords, sc, ii);
    qc.functions.register(call);
    return call;
  }

  /**
   * Creates a function literal for a user-defined function.
   * @param sf static function
   * @param qc query context
   * @param sc static context
   * @param ii input info
   * @param runtime {@code true} if this method is called at runtime
   * @param lit literal data
   * @return function item
   * @throws QueryException query exception
   */
  private static Expr userDefined(final StaticFunc sf, final QueryContext qc,
      final StaticContext sc, final InputInfo ii, final boolean runtime, final Literal lit)
          throws QueryException {

    final FuncType sft = sf.funcType();
    final int arity = lit.params.length;
    for(int a = 0; a < arity; a++) lit.add(sf.paramName(a), sft.argTypes[a], qc, ii);
    final FuncType ft = FuncType.get(lit.annotations(), sft.declType,
        Arrays.copyOf(sft.argTypes, arity));

    final StaticFuncCall call = userDefined(sf.name, lit.args, null, qc, sc, ii);
    if(call.func != null) lit.annotations = call.func.anns;
    return literal(ii, call, ft, sf.name, lit, runtime, sf.updating, false);
  }

  /**
   * Raises an error for the wrong number of function arguments.
   * @param nargs number of supplied arguments
   * @param min minimum number of allowed arguments
   * @param max maximum number of allowed arguments
   * @param function function
   * @param ii input info
   * @param literal literal
   * @throws QueryException query exception
   */
  private static void checkArity(final int nargs, final int min, final int max,
      final Object function, final InputInfo ii, final boolean literal) throws QueryException {

    if(nargs < min || nargs > max) {
      final IntList arities = new IntList();
      if(max != Integer.MAX_VALUE) {
        for(int m = min; m <= max; m++) arities.add(m);
      } else {
        arities.add(-min);
      }
      throw wrongArity(nargs, arities, function, ii, literal);
    }
  }

  /**
   * Creates a {@link Closure}, a {@link FuncItem} or a {@link FuncLit}.
   * At parse and compile time, a closure is generated to enable inlining and compilation.
   * At runtime, we directly generate a function item.
   * @param ii input info
   * @param expr function body
   * @param ft function type
   * @param name function name (can be {@code null})
   * @param lit literal data
   * @param runtime runtime flag
   * @param updating flag for updating functions
   * @param ctx context-dependent flag
   * @return the function expression
   */
  private static Expr literal(final InputInfo ii, final Expr expr, final FuncType ft,
      final QNm name, final Literal lit, final boolean runtime, final boolean updating,
      final boolean ctx) {

    final VarScope vs = lit.vs;
    final Var[] params = lit.params;
    final AnnList anns = lit.annotations();

    // context/positional access must be bound to original focus
    // example for invalid query: let $f := last#0 return (1, 2)[$f()]
    return ctx ? new FuncLit(ii, expr, ft.seqType(), name, params, anns, vs) :
      runtime ? new FuncItem(vs.sc, anns, name, params, ft, expr, vs.stackSize(), ii) :
      new Closure(ii, expr, updating ? SeqType.EMPTY_SEQUENCE_Z :
        ft != null ? ft.declType : null, name, params, anns, null, vs);
  }

  /**
   * Incorporates keywords in the argument list and checks the arity.
   * @param args positional arguments
   * @param keywords keyword arguments (can be {@code null})
   * @param names parameter names
   * @param min minimum number of allowed arguments
   * @param max maximum number of allowed arguments
   * @param function function
   * @param ii input info
   * @return arguments
   * @throws QueryException query exception
   */
  private static Expr[] prepareArgs(final Expr[] args, final QNmMap<Expr> keywords,
      final QNm[] names, final int min, final int max, final Object function,
      final InputInfo ii) throws QueryException {

    final Expr[] tmp = keywords != null ? prepareArgs(args, keywords, names, function, ii) :
      args;
    final int arity = tmp.length;
    for(int a = arity - 1; a >= 0; a--) {
      if(tmp[a] == null) {
        if(a < min) throw ARGMISSING_X_X.get(ii, function, names[a].prefixString());
        tmp[a] = Empty.UNDEFINED;
      }
    }
    checkArity(arity, min, max, function, ii, false);
    return tmp;
  }

  /**
   * Container for function literals.
   *
   * @author BaseX Team 2005-23, BSD License
   * @author Christian Gruen
   */
  private static class Literal {
    /** Variable scope. */
    final VarScope vs;
    /** Parameters. */
    final Var[] params;
    /** Arguments. */
    final Expr[] args;
    /** Annotations. */
    AnnList annotations;
    /** Parameter counter. */
    int a;

    /**
     * Constructor.
     * @param sc static context
     * @param arity arity
     */
    Literal(final StaticContext sc, final int arity) {
      vs = new VarScope(sc);
      params = new Var[arity];
      args = new Expr[arity];
    }

    /**
     * Adds a parameter and argument.
     * @param name parameter name
     * @param st parameter type
     * @param qc query context
     * @param ii input info
     */
    void add(final QNm name, final SeqType st, final QueryContext qc, final InputInfo ii) {
      final Var var = vs.addNew(name, st, true, qc, ii);
      params[a] = var;
      args[a] = new VarRef(ii, var);
      a++;
    }

    /**
     * Returns the annotations.
     * @return annotations
     */
    AnnList annotations() {
      if(annotations == null) annotations = new AnnList();
      return annotations;
    }
  }
}
