package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@AnalyzeClasses(packages = "gal.conxugal")
class EntityIdentityArchTest {

  // By name, so this module needs no compile dependency on Micronaut Data.
  private static final String MAPPED_ENTITY = "io.micronaut.data.annotation.MappedEntity";
  private static final String ID = "io.micronaut.data.annotation.Id";
  private static final String GENERATED_VALUE = "io.micronaut.data.annotation.GeneratedValue";

  @ArchTest
  static final ArchRule ENTITIES_COMPARE_BY_THEIR_ID =
      classes()
          .that()
          .areAnnotatedWith(MAPPED_ENTITY)
          .and()
          .areRecords()
          .should(compareByTheirIdRatherThanEveryField());

  private static ArchCondition<JavaClass> compareByTheirIdRatherThanEveryField() {
    return new ArchCondition<>("compare by their @Id rather than by every field") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        Optional<JavaMethod> equals = javaClass.tryGetMethod("equals", Object.class);
        Optional<JavaMethod> hashCode = javaClass.tryGetMethod("hashCode");
        boolean overridesEquality =
            isDeclaredExplicitly(equals) && isDeclaredExplicitly(hashCode);
        Set<String> ownIdFields = ownIdFieldNames(javaClass);

        // A record whose every @Id is another aggregate's id holds no identity of its own: it is
        // a single value filed under its owner, and two such values differ exactly when their
        // contents do. The record's own equality is therefore the correct equality, and an
        // override comparing by the owner column would make a value equal to itself across the
        // very write that changes it.
        if (ownIdFields.isEmpty()) {
          events.add(
              new SimpleConditionEvent(
                  javaClass,
                  !overridesEquality,
                  ("%s keys only on its owner's id, so it is a value rather than an entity and "
                          + "compares by every field. Remove its equals and hashCode and let the "
                          + "record compare by value.")
                      .formatted(javaClass.getFullName())));
          return;
        }

        if (!overridesEquality) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  ("%s inherits the record's equals, so it compares by every field rather than by "
                          + "its @Id. An entity is the same entity however its other fields "
                          + "differ. Override both, without the 'final' modifier.")
                      .formatted(javaClass.getFullName())));
          return;
        }
        Set<String> read = fieldsReadBy(equals.get());
        // Any one is enough: an aggregate has a single id, and a value held inside an aggregate
        // keys on the natural part of a composite key, ignoring the column that files its row
        // under its owner.
        boolean satisfied = ownIdFields.stream().anyMatch(read::contains);
        events.add(
            new SimpleConditionEvent(
                javaClass,
                satisfied,
                "%s declares equals but reads none of %s, so it cannot be comparing by identity"
                    .formatted(javaClass.getFullName(), ownIdFields)));
      }
    };
  }

  /**
   * The {@code @Id} components that carry identity of this record's own, rather than filing its
   * row under an owner. An id the database assigns is its own by definition; so is any other
   * {@code @Id} that is not one of the domain's aggregate-id wrappers — the natural key of a value
   * held inside an aggregate, such as {@code NomeAlternativo}'s name.
   */
  private static Set<String> ownIdFieldNames(JavaClass javaClass) {
    return javaClass.getFields().stream()
        .filter(field -> field.isAnnotatedWith(ID))
        .filter(
            field ->
                field.isAnnotatedWith(GENERATED_VALUE) || !isAggregateId(field.getRawType()))
        .map(JavaField::getName)
        .collect(Collectors.toSet());
  }

  private static boolean isAggregateId(JavaClass type) {
    return type.getPackageName().startsWith("gal.conxugal.domain")
        && type.getSimpleName().endsWith("Id");
  }

  /**
   * A record's implicit {@code equals}/{@code hashCode} are emitted by javac as real declared
   * methods, so their presence proves nothing. The modifier is what tells the two apart: the
   * implicit ones are {@code public final}, an explicitly declared override is not. This holds
   * only for records, which is why the rule above does not look at any other kind of class.
   */
  private static boolean isDeclaredExplicitly(Optional<JavaMethod> method) {
    return method.isPresent() && !method.get().getModifiers().contains(JavaModifier.FINAL);
  }

  private static Set<String> fieldsReadBy(JavaMethod method) {
    return method.getFieldAccesses().stream()
        .map(access -> access.getTarget().getName())
        .collect(Collectors.toSet());
  }

}
