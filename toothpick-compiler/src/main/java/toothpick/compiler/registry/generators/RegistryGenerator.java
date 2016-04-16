package toothpick.compiler.registry.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import toothpick.compiler.CodeGenerator;
import toothpick.compiler.registry.targets.RegistryInjectionTarget;
import toothpick.registries.FactoryRegistry;
import toothpick.registries.MemberInjectorRegistry;

/**
 * Generates a Registry for a given {@link RegistryInjectionTarget}.
 *
 * @see {@link FactoryRegistry} and {@link MemberInjectorRegistry} for Registry types.
 */
public class RegistryGenerator implements CodeGenerator {

  private RegistryInjectionTarget registryInjectionTarget;

  public RegistryGenerator(RegistryInjectionTarget registryInjectionTarget) {
    this.registryInjectionTarget = registryInjectionTarget;
  }

  @Override
  public String brewJava() {
    TypeSpec.Builder registryTypeSpec = TypeSpec.classBuilder(registryInjectionTarget.registryName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .superclass(ClassName.get(registryInjectionTarget.superClass));

    emitConstructor(registryTypeSpec);
    emitGetFactoryMethod(registryTypeSpec);

    JavaFile javaFile = JavaFile.builder(registryInjectionTarget.packageName, registryTypeSpec.build())
        .addFileComment("Generated code from Toothpick. Do not modify!")
        .build();

    return javaFile.toString();
  }

  private void emitConstructor(TypeSpec.Builder registryTypeSpec) {
    MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

    CodeBlock.Builder iterateChildAddRegistryBlock = CodeBlock.builder();
    for (String childPackageName : registryInjectionTarget.childrenRegistryPackageNameList) {
      ClassName registryClassName = ClassName.get(childPackageName, registryInjectionTarget.registryName);
      iterateChildAddRegistryBlock.addStatement("addChildRegistry(new $L())", registryClassName);
    }

    constructor.addCode(iterateChildAddRegistryBlock.build());
    registryTypeSpec.addMethod(constructor.build());
  }

  private void emitGetFactoryMethod(TypeSpec.Builder registryTypeSpec) {
    TypeVariableName t = TypeVariableName.get("T");
    MethodSpec.Builder getMethod = MethodSpec.methodBuilder(registryInjectionTarget.getterName)
        .addTypeVariable(t)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), t), "clazz")
        .returns(ParameterizedTypeName.get(ClassName.get(registryInjectionTarget.type), t));

    CodeBlock.Builder switchBlockBuilder = CodeBlock.builder().beginControlFlow("switch($L)", "clazz.getName()");

    for (TypeElement injectionTarget : registryInjectionTarget.injectionTargetList) {
      switchBlockBuilder.add("case ($S):\n", injectionTarget.getQualifiedName().toString());
      String typeSimpleName = registryInjectionTarget.type.getSimpleName();
      switchBlockBuilder.addStatement("return ($L<T>) new $L$$$$$L()", typeSimpleName, ClassName.get(injectionTarget), typeSimpleName);
    }

    switchBlockBuilder.add("default:\n");
    switchBlockBuilder.addStatement("return $L(clazz)", registryInjectionTarget.childrenGetterName);
    switchBlockBuilder.endControlFlow();
    getMethod.addCode(switchBlockBuilder.build());
    registryTypeSpec.addMethod(getMethod.build());
  }

  @Override
  public String getFqcn() {
    return registryInjectionTarget.getFqcn();
  }
}