package com.jetbrains.jetpad.vclang.naming.scope;

import com.jetbrains.jetpad.vclang.term.Abstract;

import java.util.HashSet;
import java.util.Set;

public class FilteredScope implements Scope {
  private final Scope myScope;
  private final Set<String> myNames;
  private final boolean myInclude;

  public FilteredScope(Scope scope, Set<String> names, boolean include) {
    myScope = scope;
    myNames = names;
    myInclude = include;
  }

  @Override
  public Set<String> getNames() {
    Set<String> names = new HashSet<>(myScope.getNames());
    if (myInclude) {
      names.retainAll(myNames);
    } else {
      names.removeAll(myNames);
    }
    return names;
  }

  @Override
  public Abstract.Definition resolveName(String name) {
    if (myInclude) {
      return myNames.contains(name) ? myScope.resolveName(name) : null;
    } else {
      return myNames.contains(name) ? null : myScope.resolveName(name);
    }
  }
}
