package com.jetbrains.jetpad.vclang.term.error;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.definition.Binding;
import com.jetbrains.jetpad.vclang.term.definition.Definition;
import com.jetbrains.jetpad.vclang.term.expr.Expression;

import java.util.ArrayList;
import java.util.List;

public class GoalError extends TypeCheckingError {
  private final List<Binding> myContext;
  private final Expression myType;

  public GoalError(Definition parent, List<Binding> context, Expression type, Abstract.PrettyPrintableSourceNode expression) {
    super(parent, "Goal", expression, getNames(context));
    myContext = new ArrayList<>(context);
    myType = type;
  }

  @Override
  public String toString() {
    if (myContext.isEmpty() && myType == null) {
      return printPosition() + getMessage();
    }

    StringBuilder builder = new StringBuilder();
    builder.append(printPosition()).append(getMessage());
    if (myType != null) {
      builder.append("\n\tExpected type: ");
      List<String> names = new ArrayList<>(myContext.size());
      for (Binding binding : myContext) {
        names.add(binding.getName());
      }
      myType.prettyPrint(builder, names, Abstract.Expression.PREC);
    }

    if (!myContext.isEmpty()) {
      builder.append("\n\tContext:");
      List<String> names = new ArrayList<>(myContext.size());
      for (Binding binding : myContext) {
        builder.append("\n\t\t").append(binding.getName() == null ? "_" : binding.getName()).append(" : ");
        Expression type = binding.getType();
        if (type != null) {
          type.prettyPrint(builder, names, Abstract.Expression.PREC);
        } else {
          builder.append("{!error}");
        }
        names.add(binding.getName());
      }
    }

    return builder.toString();
  }
}
