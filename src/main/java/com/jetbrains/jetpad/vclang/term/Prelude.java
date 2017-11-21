package com.jetbrains.jetpad.vclang.term;

import com.jetbrains.jetpad.vclang.core.context.binding.LevelVariable;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.EmptyDependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.TypedSingleDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.definition.DataDefinition;
import com.jetbrains.jetpad.vclang.core.definition.Definition;
import com.jetbrains.jetpad.vclang.core.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.core.elimtree.BranchElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.ElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.IntervalElim;
import com.jetbrains.jetpad.vclang.core.elimtree.LeafElimTree;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.sort.Level;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter;
import com.jetbrains.jetpad.vclang.frontend.ConcreteReferableProvider;
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable;
import com.jetbrains.jetpad.vclang.naming.scope.Scope;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState;
import com.jetbrains.jetpad.vclang.typechecking.Typechecking;
import com.jetbrains.jetpad.vclang.util.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.parameter;

public class Prelude {
  public static DataDefinition INTERVAL;
  public static Constructor LEFT, RIGHT;

  public static DataDefinition NAT;
  public static Constructor ZERO, SUC;

  public static FunctionDefinition COERCE;

  public static DataDefinition PATH;
  public static FunctionDefinition PATH_INFIX;
  public static Constructor PATH_CON;

  public static FunctionDefinition AT;
  public static FunctionDefinition ISO;

  public static DataDefinition PROP_TRUNC;
  public static DataDefinition SET_TRUNC;

  public static Constructor PROP_TRUNC_PATH_CON;
  public static Constructor SET_TRUNC_PATH_CON;

  private Prelude() {
  }

  public static void update(Definition definition) {
    switch (definition.getReferable().textRepresentation()) {
      case "Nat":
        NAT = (DataDefinition) definition;
        ZERO = NAT.getConstructor("zero");
        SUC = NAT.getConstructor("suc");
        break;
      case "I":
        INTERVAL = (DataDefinition) definition;
        INTERVAL.setSort(Sort.PROP);
        INTERVAL.setMatchesOnInterval();
        LEFT = INTERVAL.getConstructor("left");
        RIGHT = INTERVAL.getConstructor("right");
        break;
      case "Path":
        PATH = (DataDefinition) definition;
        PATH.setSort(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR, -1)));
        PATH_CON = PATH.getConstructor("path");
        break;
      case "=":
        PATH_INFIX = (FunctionDefinition) definition;
        break;
      case "@": {
        AT = (FunctionDefinition) definition;
        DependentLink atParams = AT.getParameters().subst(new ExprSubstitution(), LevelSubstitution.EMPTY, 3);
        SingleDependentLink intervalParam = new TypedSingleDependentLink(true, "i", ExpressionFactory.Interval());
        DependentLink pathParam = parameter("f", new PiExpression(Sort.STD, intervalParam, new AppExpression(new ReferenceExpression(atParams), new ReferenceExpression(intervalParam))));
        pathParam.setNext(parameter("i", ExpressionFactory.Interval()));
        Map<Constructor, ElimTree> children = Collections.singletonMap(PATH_CON, new LeafElimTree(pathParam, new AppExpression(new ReferenceExpression(pathParam), new ReferenceExpression(pathParam.getNext()))));
        ElimTree otherwise = new BranchElimTree(atParams, children);
        AT.setBody(new IntervalElim(AT.getParameters(), Collections.singletonList(new Pair<>(new ReferenceExpression(AT.getParameters().getNext()), new ReferenceExpression(AT.getParameters().getNext().getNext()))), otherwise));
        AT.setStatus(Definition.TypeCheckingStatus.NO_ERRORS);
        break;
      }
      case "coe":
        COERCE = (FunctionDefinition) definition;
        DependentLink coeParams = COERCE.getParameters().subst(new ExprSubstitution(), LevelSubstitution.EMPTY, 2);
        COERCE.setBody(new BranchElimTree(coeParams, Collections.singletonMap(LEFT, new LeafElimTree(EmptyDependentLink.getInstance(), new ReferenceExpression(coeParams.getNext())))));
        COERCE.setStatus(Definition.TypeCheckingStatus.NO_ERRORS);
        break;
      case "iso": {
        ISO = (FunctionDefinition) definition;
        DependentLink isoParams = ISO.getParameters().subst(new ExprSubstitution(), LevelSubstitution.EMPTY, 6);
        Map<Constructor, ElimTree> children = new HashMap<>();
        children.put(LEFT, new LeafElimTree(EmptyDependentLink.getInstance(), new ReferenceExpression(isoParams)));
        children.put(RIGHT, new LeafElimTree(EmptyDependentLink.getInstance(), new ReferenceExpression(isoParams.getNext())));
        ISO.setBody(new BranchElimTree(isoParams, children));
        ISO.setResultType(new UniverseExpression(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR))));
        ISO.setStatus(Definition.TypeCheckingStatus.NO_ERRORS);
        break;
      }
      case "TrP":
        PROP_TRUNC = (DataDefinition) definition;
        PROP_TRUNC.setSort(Sort.PROP);
        PROP_TRUNC_PATH_CON = PROP_TRUNC.getConstructor("truncP");
        break;
      case "TrS":
        SET_TRUNC = (DataDefinition) definition;
        SET_TRUNC.setSort(Sort.SetOfLevel(new Level(LevelVariable.PVAR)));
        SET_TRUNC_PATH_CON = SET_TRUNC.getConstructor("truncS");
        break;
    }
  }

  // This works only because currently the prelude namespace is flat.
  // This solution is flat from perfect, but someone on our team said it was fine to do that.
  public static void initialise(Scope scope, TypecheckerState state) {
    for (String name : new String[]{"Nat", "I", "Path", "=", "@", "coe", "iso", "TrP", "TrS"}) {
      update(state.getTypechecked((GlobalReferable) scope.resolveName(name)));
    }
  }

  public static class PreludeTypechecking extends Typechecking {
    public PreludeTypechecking(TypecheckerState state) {
      super(state, ConcreteReferableProvider.INSTANCE, DummyErrorReporter.INSTANCE);
    }

    @Override
    public void typecheckingFinished(Definition definition) {
      update(definition);
    }
  }
}
