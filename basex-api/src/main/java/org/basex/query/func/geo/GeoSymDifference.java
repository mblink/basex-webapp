package org.basex.query.func.geo;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-22, BSD License
 * @author Christian Gruen
 */
public final class GeoSymDifference extends GeoFn {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    return toElement(toGeometry(0, qc).symDifference(toGeometry(1, qc)));
  }
}
