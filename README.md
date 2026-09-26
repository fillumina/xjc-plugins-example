[![Maven Central: cxf-validation-frontend](https://img.shields.io/maven-central/v/com.fillumina/cxf-validation-frontend.svg)](https://central.sonatype.com/artifact/com.fillumina/cxf-validation-frontend) [![Maven Central: xjc-bean-validation-plugin](https://img.shields.io/maven-central/v/com.fillumina/xjc-bean-validation-plugin.svg)](https://central.sonatype.com/artifact/com.fillumina/xjc-bean-validation-plugin) [![Maven Central: xjc-primitives-plugin](https://img.shields.io/maven-central/v/com.fillumina/xjc-primitives-plugin.svg)](https://central.sonatype.com/artifact/com.fillumina/xjc-primitives-plugin)

# xjc-plugins-example

The three badges are the releases of the three plugins this reactor pins, the ones a build
resolves from Central.

This Maven reactor contains two consumer examples. `three-plugins` generates one WSDL with
[`cxf-validation-frontend`](https://github.com/fillumina/cxf-validation-frontend),
[`xjc-bean-validation-plugin`](https://github.com/fillumina/xjc-bean-validation-plugin) and
[`xjc-primitives-plugin`](https://github.com/fillumina/xjc-primitives-plugin), then repeats it with
its two XJC options reversed. `equals-hashcode` tests primitive replacement before the XJC 4
equals and hashCode plugins. Each module has its own generated sources, dependencies and tests.

## What the krasa plugin did, and what does it now

`com.fillumina:krasa-jaxb-tools` was one artifact that did all three things. The new line is three
artifacts, and the `three-plugins` module shows them working together:

| krasa-jaxb-tools | the new line |
| --- | --- |
| `-XJsr303Annotations`, writing the constraints the schema states | `xjc-bean-validation-plugin`, `-xjc-XBeanValidationAnnotations` |
| `-XReplacePrimitives`, in the same artifact | `xjc-primitives-plugin`, `-xjc-XReplacePrimitives` |
| the `krasa` frontend, `ValidSEIGenerator`, for the validated interface | `cxf-validation-frontend`, `-frontend bean-validation` |

The following is from `three-plugins/pom.xml` (which also generates the WSDL a second time with the two XJC plugins in the opposite order):

```xml
<plugin>
  <groupId>org.apache.cxf</groupId>
  <artifactId>cxf-codegen-plugin</artifactId>
  <version>4.2.3</version>
  <executions>
    <execution>
      <id>wsdl2java</id>
      <phase>generate-sources</phase>
      <configuration>
        <wsdlOptions>
          <wsdlOption>
            <wsdl>${project.basedir}/wsdl/Order.wsdl</wsdl>
            <extraargs>
              <extraarg>-frontend</extraarg>
              <extraarg>bean-validation</extraarg>
              <extraarg>-xjc-XCxfValidationFrontendOptions:generateAnnotations=both</extraarg>
              <extraarg>-xjc-XBeanValidationAnnotations</extraarg>
              <extraarg>-xjc-XBeanValidationAnnotations:omitJavaTypeBounds=true</extraarg>
              <extraarg>-xjc-XReplacePrimitives</extraarg>
            </extraargs>
          </wsdlOption>
        </wsdlOptions>
      </configuration>
      <goals>
        <goal>wsdl2java</goal>
      </goals>
    </execution>
  </executions>
  <dependencies>
    <dependency>
      <groupId>com.fillumina</groupId>
      <artifactId>cxf-validation-frontend</artifactId>
      <version>${cxf-validation-frontend.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fillumina</groupId>
      <artifactId>xjc-bean-validation-plugin</artifactId>
      <version>${xjc-bean-validation-plugin.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fillumina</groupId>
      <artifactId>xjc-primitives-plugin</artifactId>
      <version>${xjc-primitives-plugin.version}</version>
    </dependency>
  </dependencies>
</plugin>
```

Each plugin is activated and configured the way its own README documents. The three names are
distinct, which is what lets them run together: XJC switches on the first plugin that answers to an
option name, so two plugins sharing one would mean one of them silently never runs.

## What the tests prove

The `three-plugins` module generates the same WSDL twice into separate packages, once with bean validation before primitive replacement and once with the XJC options reversed. `TheThreePluginsWorkTogetherTest` compares the compiled models and checks the first one end to end:

1. the constraints of the schema are on the generated types — `@Pattern` on `code`, `@Size(max = 200)` on `note`, `@NotNull` and `@Valid` on the nested `customer`;
2. the primitive fields are boxed — `quantity` is an `Integer` and `express` is a `Boolean`, so `@NotNull` on them can mean something. The compiled `express` property remains readable through JavaBeans introspection, and a JAXB marshal/unmarshal round trip preserves it with field access;
3. the generated service interface carries `@Valid`, on the method and on its parameter;
4. Hibernate Validator reports an order that breaks the schema — a malformed code and a malformed email — so the annotations are live, not just written.

The `equals-hashcode` module uses `org.jvnet.jaxb:jaxb-plugins:4.0.16` with
`-XReplacePrimitives` **before** `-Xequals` and `-XhashCode`. Its generated strategy-based methods
need `jaxb-plugins-runtime` as a compile/runtime dependency; keeping it in this module prevents
that dependency from leaking into the three-plugin example. The test checks compiled equality and
hash codes for nullable boxed integers and booleans, each separately against null, a changed
boolean, and a changed string. The generated boolean is also readable by JavaBeans introspection.

The order matters: with equals/hashCode before primitive replacement, the generated `equals` method
unboxes a null `Integer` and fails with a `NullPointerException`. The test caught that when the order
was temporarily reversed. The supported-order test checks `count` and `active` separately so that
losing either comparison is a failure. This is not a claim that every third-party XJC plugin works.

## Failures these examples can detect

1. In `three-plugins`, a missing field constraint, primitive boxing, service `@Valid`, or JavaBeans
   boolean getter fails an assertion on compiled output. Its JAXB round trip also checks field
   binding. A difference in the generated fields of the two XJC option orders fails the comparison.
2. In `equals-hashcode`, a generated method that treats `null` as `0` or `false`, skips one of those
   fields, or violates the equals/hashCode contract fails its runtime test. Putting equals/hashCode
   *before* primitive replacement was deliberately tried and failed with an `Integer.intValue()`
   `NullPointerException` in generated `SampleType.equals`.
3. The strategy-based methods import classes from `jaxb-plugins-runtime`. The module declares
   that jar as an ordinary dependency so the generated application can compile and run; a
   codegen-only dependency would not supply it to that application.

CI tests only the supported third-party order and the two orders of our own XJC plugins. It does
not promise compatibility with every plugin that changes accessors or generated property types.

## Building


The parent reactor needs JDK 21 and Maven. From this directory, one command builds and tests
both modules:

```
mvn -B clean verify -Dcxf-validation-frontend.version=<v> -Dxjc-bean-validation-plugin.version=<v> -Dxjc-primitives-plugin.version=<v>
```

CI installs snapshots of the three plugins from their latest commits, then uses two named Maven
steps: `-pl three-plugins -am` for composition and order independence, and
`-pl equals-hashcode -am` for the supported external-plugin order. Both run `clean verify`; a
failure names the affected module in GitHub Actions. The deliberately broken third-party order is
not run in CI.

The three versions are properties, so the example runs against releases from Maven Central and
against snapshots without being touched. The snapshot side needs `mvn install` in each of the three
projects first:

```
cd ../cxf-validation-frontend && mvn -B install
cd ../xjc-bean-validation-plugin && mvn -B install
cd ../xjc-primitives-plugin && mvn -B install
cd ../xjc-plugins-example && mvn -B clean verify
```

## The single-plugin examples

Each of the three has an example of its own, where the plugin is the only thing the build does:
[`cxf-validation-frontend-example`](https://github.com/fillumina/cxf-validation-frontend-example),
[`xjc-bean-validation-plugin-example`](https://github.com/fillumina/xjc-bean-validation-plugin-example)
and [`xjc-primitives-plugin-example`](https://github.com/fillumina/xjc-primitives-plugin-example).
The `three-plugins` module is the composition. The `equals-hashcode` module is the separate check for one external XJC plugin combination.
