package com.jetbrains.jetpad.vclang;

import com.jetbrains.jetpad.vclang.error.GeneralError;
import com.jetbrains.jetpad.vclang.error.ListErrorReporter;
import com.jetbrains.jetpad.vclang.module.*;
import com.jetbrains.jetpad.vclang.module.output.FileOutputSupplier;
import com.jetbrains.jetpad.vclang.module.source.FileSource;
import com.jetbrains.jetpad.vclang.module.source.FileSourceSupplier;
import com.jetbrains.jetpad.vclang.module.source.Source;
import com.jetbrains.jetpad.vclang.module.source.SourceSupplier;
import com.jetbrains.jetpad.vclang.module.utils.FileOperations;
import com.jetbrains.jetpad.vclang.naming.NameResolver;
import com.jetbrains.jetpad.vclang.naming.namespace.SimpleDynamicNamespaceProvider;
import com.jetbrains.jetpad.vclang.naming.namespace.SimpleModuleNamespaceProvider;
import com.jetbrains.jetpad.vclang.naming.namespace.SimpleNamespace;
import com.jetbrains.jetpad.vclang.naming.namespace.SimpleStaticNamespaceProvider;
import com.jetbrains.jetpad.vclang.naming.oneshot.OneshotNameResolver;
import com.jetbrains.jetpad.vclang.naming.oneshot.OneshotSourceInfoCollector;
import com.jetbrains.jetpad.vclang.serialization.ModuleDeserialization;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.ConcreteResolveListener;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.term.definition.visitor.DefinitionResolveStaticModVisitor;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState;
import com.jetbrains.jetpad.vclang.typechecking.order.TypecheckingOrdering;
import com.jetbrains.jetpad.vclang.typechecking.staticmodresolver.ConcreteStaticModListener;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConsoleMain {
  public static void main(String[] args) {
    Options cmdOptions = new Options();
    cmdOptions.addOption("h", "help", false, "print this message");
    cmdOptions.addOption(Option.builder("s").longOpt("source").hasArg().argName("dir").desc("source directory").build());
    cmdOptions.addOption(Option.builder("o").longOpt("output").hasArg().argName("dir").desc("output directory").build());
    cmdOptions.addOption(Option.builder("L").hasArg().argName("dir").desc("add <dir> to the list of directories searched for libraries").build());
    cmdOptions.addOption(Option.builder().longOpt("recompile").desc("recompile files").build());
    CommandLineParser cmdParser = new DefaultParser();
    CommandLine cmdLine;
    try {
      cmdLine = cmdParser.parse(cmdOptions, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      return;
    }

    if (cmdLine.hasOption("h")) {
      new HelpFormatter().printHelp("vclang [FILES]", cmdOptions);
    }

    String sourceDirStr = cmdLine.getOptionValue("s");
    final File sourceDir = new File(sourceDirStr == null ? System.getProperty("user.dir") : sourceDirStr);
    String outputDirStr = cmdLine.getOptionValue("o");
    File outputDir = outputDirStr == null ? sourceDir : new File(outputDirStr);
    boolean recompile = cmdLine.hasOption("recompile");

    List<File> libDirs = new ArrayList<>();
    String workingDir = System.getenv("AppData");
    File workingPath = null;
    if (workingDir != null) {
      workingPath = new File(workingDir, "vclang");
    } else {
      workingDir = System.getProperty("user.home");
      if (workingDir != null) {
        workingPath = new File(workingDir, ".vclang");
      }
    }
    if (cmdLine.getOptionValues("L") != null) {
      for (String dir : cmdLine.getOptionValues("L")) {
        libDirs.add(new File(dir));
      }
    }
    if (workingPath != null) {
      libDirs.add(new File(workingPath, "lib"));
    }

    SimpleStaticNamespaceProvider staticNsProvider = new SimpleStaticNamespaceProvider();
    final SimpleModuleNamespaceProvider moduleNsProvider = new SimpleModuleNamespaceProvider();
    final ListErrorReporter errorReporter = new ListErrorReporter();
    final NameResolver nameResolver = new NameResolver(moduleNsProvider, staticNsProvider, new SimpleDynamicNamespaceProvider());
    final OneshotNameResolver oneshotNameResolver = new OneshotNameResolver(errorReporter, nameResolver, new ConcreteResolveListener(), new SimpleStaticNamespaceProvider(), new SimpleDynamicNamespaceProvider());
    final OneshotSourceInfoCollector srcInfoCollector = new OneshotSourceInfoCollector();
    final ErrorFormatter errf = new ErrorFormatter(srcInfoCollector.sourceInfoProvider);
    final List<ModuleID> loadedModules = new ArrayList<>();
    final List<Abstract.Definition> modulesToTypeCheck = new ArrayList<>();
    final BaseModuleLoader moduleLoader = new BaseModuleLoader(recompile) {
      @Override
      public void loadingError(GeneralError error) {
        errorReporter.report(error);
      }

      @Override
      public void loadingSucceeded(ModuleID module, Abstract.ClassDefinition abstractDefinition) {
        if (abstractDefinition != null) {
          DefinitionResolveStaticModVisitor rsmVisitor = new DefinitionResolveStaticModVisitor(new ConcreteStaticModListener());
          rsmVisitor.visitClass(abstractDefinition, true);

          oneshotNameResolver.visitModule(abstractDefinition);
          srcInfoCollector.visitModule(module, abstractDefinition);

          modulesToTypeCheck.add(abstractDefinition);
        }
        moduleNsProvider.registerModule(module.getModulePath(), abstractDefinition);
        loadedModules.add(module);
        System.out.println("[Loaded] " + module.getModulePath());
      }
    };

    Prelude.moduleID = new FileModuleID(new ModulePath("Prelude"));
    moduleLoader.setSourceSupplier(new SourceSupplier() {
      @Override
      public Source getSource(ModuleID module) {
        return new FileSource(errorReporter, Prelude.moduleID, FileOperations.getFile(new File(Paths.get("").toAbsolutePath().toFile(), "lib"), module.getModulePath(), FileOperations.EXTENSION));
      }

      @Override
      public ModuleID locateModule(ModulePath modulePath) {
        return new FileModuleID(modulePath);
      }
    });
    Prelude.PRELUDE = (SimpleNamespace) staticNsProvider.forDefinition(moduleLoader.load(Prelude.moduleID).abstractDefinition);

    if (!errorReporter.getErrorList().isEmpty()) {
      for (GeneralError error : errorReporter.getErrorList()) {
        System.err.println(errf.printError(error));
      }
      return;
    }

    TypecheckerState state = new TypecheckerState();
    if (!TypecheckingOrdering.typecheck(state, modulesToTypeCheck, errorReporter, true)) {
      return;
    }
    modulesToTypeCheck.clear();
    errorReporter.getErrorList().clear();

    ModuleDeserialization moduleDeserialization = new ModuleDeserialization();
    moduleLoader.setSourceSupplier(new FileSourceSupplier(errorReporter, sourceDir));
    moduleLoader.setOutputSupplier(new FileOutputSupplier(moduleDeserialization, outputDir, libDirs));

    if (cmdLine.getArgList().isEmpty()) {
      if (sourceDirStr == null) return;
      try {
        Files.walkFileTree(sourceDir.toPath(), new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
            if (path.getFileName().toString().endsWith(FileOperations.EXTENSION)) {
              processFile(moduleLoader, errorReporter, errf, path, sourceDir);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
            System.err.println(GeneralError.ioError(e));
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        System.err.println(GeneralError.ioError(e));
      }
    } else {
      for (String fileName : cmdLine.getArgList()) {
        processFile(moduleLoader, errorReporter, errf, Paths.get(fileName), sourceDir);
      }
    }

    final Set<ModuleID> failedModules = new HashSet<>();

    TypecheckingOrdering.typecheck(state, modulesToTypeCheck, errorReporter, new TypecheckedReporter() {
      @Override
      public void typecheckingSucceeded(Abstract.Definition definition) {
      }

      @Override
      public void typecheckingFailed(Abstract.Definition definition) {
        failedModules.add(srcInfoCollector.sourceInfoProvider.moduleOf(definition));
        for (GeneralError error : errorReporter.getErrorList()) {
          System.err.println(errf.printError(error));
        }
        errorReporter.getErrorList().clear();
      }
    }, false);

    for (ModuleID moduleID : loadedModules) {
      StringBuilder builder = new StringBuilder();
      builder.append("[").append(failedModules.contains(moduleID) ? "✗" : " ").append("]")
             .append(" ").append(moduleID.getModulePath())
             .append(" (").append(moduleID).append(")");
      System.out.println(builder);
    }

    for (GeneralError error : errorReporter.getErrorList()) {
      System.err.println(errf.printError(error));
    }

    for (ModuleID moduleID : loadedModules) {
      //moduleLoader.save(moduleID);  // FIXME[serial]
    }
  }

  static private ModulePath getModule(Path file) {
    int nameCount = file.getNameCount();
    if (nameCount < 1) return null;
    List<String> names = new ArrayList<>(nameCount);
    for (int i = 0; i < nameCount; ++i) {
      String name = file.getName(i).toString();
      if (i == nameCount - 1) {
        if (!name.endsWith(FileOperations.EXTENSION)) return null;
        name = name.substring(0, name.length() - 3);
      }

      if (name.length() == 0 || !(Character.isLetterOrDigit(name.charAt(0)) || name.charAt(0) == '_')) return null;
      for (int j = 1; j < name.length(); ++j) {
        if (!(Character.isLetterOrDigit(name.charAt(j)) || name.charAt(j) == '_' || name.charAt(j) == '-' || name.charAt(j) == '\'')) return null;
      }
      names.add(name);
    }
    return new ModulePath(names);
  }

  static private void processFile(ModuleLoader moduleLoader, ListErrorReporter errorReporter, ErrorFormatter errf, Path fileName, File sourceDir) {
    Path relativePath = sourceDir != null && fileName.startsWith(sourceDir.toPath()) ? sourceDir.toPath().relativize(fileName) : fileName;
    ModulePath modulePath = getModule(relativePath);
    if (modulePath == null) {
      System.err.println(fileName + ": incorrect file name");
      return;
    }

    ModuleID moduleID = moduleLoader.locateModule(modulePath);
    if (moduleID == null) {
      throw new IllegalStateException();
    }
    moduleLoader.load(moduleID);

    for (GeneralError error : errorReporter.getErrorList()) {
      System.err.println(errf.printError(error));
    }

    errorReporter.getErrorList().clear();
  }
}
