package com.jetbrains.jetpad.vclang.typechecking.typeclass;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.core.expr.Expression;

import java.util.Arrays;
import java.util.List;

public class CompositeInstancePool implements ClassViewInstancePool {
  private final List<ClassViewInstancePool> myPools;

  public CompositeInstancePool(ClassViewInstancePool... pools) {
    myPools = Arrays.asList(pools);
  }

  @Override
  public Expression getInstance(Expression classifyingExpression, Abstract.ClassView classView) {
    for (ClassViewInstancePool pool : myPools) {
      Expression expr = pool.getInstance(classifyingExpression, classView);
      if (expr != null) {
        return expr;
      }
    }
    return null;
  }
}
