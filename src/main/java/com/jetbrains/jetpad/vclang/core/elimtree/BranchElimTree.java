package com.jetbrains.jetpad.vclang.core.elimtree;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;

import java.util.Collection;
import java.util.Map;

public class BranchElimTree extends ElimTree {
  private final Map<Constructor, ElimTree> myChildren;

  public BranchElimTree(DependentLink parameters, Map<Constructor, ElimTree> children) {
    super(parameters);
    myChildren = children;
  }

  public ElimTree getChild(Constructor constructor) {
    return myChildren.get(constructor);
  }

  public Collection<Map.Entry<Constructor, ElimTree>> getChildren() {
    return myChildren.entrySet();
  }
}