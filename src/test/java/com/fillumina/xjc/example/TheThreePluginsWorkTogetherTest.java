package com.fillumina.xjc.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.order.CustomerType;
import com.example.order.OrderRequestType;
import com.example.order.OrderServicePortType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The three plugins of the new line inside one build, over one WSDL: the frontend puts {@code @Valid}
 * on the generated service interface, the bean validation plugin writes the constraints the schema
 * states, and the primitives plugin boxes the primitive fields so that a constraint can be put on
 * them.
 *
 * <p>What the krasa-jaxb-tools plugin did alone in one artifact, here three artifacts do together,
 * wired the way each of their READMEs documents.
 */
class TheThreePluginsWorkTogetherTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void theConstraintsOfTheSchemaAreWrittenOnTheGeneratedTypes() throws Exception {
        assertEquals("[A-Z]{2}[0-9]{5}", annotation("code", Pattern.class).regexp());
        assertNotNull(annotation("code", NotNull.class));
        assertEquals(200, annotation("note", Size.class).max());
        assertNotNull(annotation("customer", NotNull.class));
        assertNotNull(annotation("customer", Valid.class));
    }

    @Test
    void thePrimitiveFieldsAreBoxed() throws Exception {
        assertEquals(Integer.class, OrderRequestType.class.getDeclaredField("quantity").getType());
        assertEquals(Boolean.class, OrderRequestType.class.getDeclaredField("express").getType());
    }

    @Test
    void theServiceInterfaceCarriesTheAnnotation() throws Exception {
        Method placeOrder =
                OrderServicePortType.class.getMethod("placeOrder", OrderRequestType.class);

        assertNotNull(placeOrder.getAnnotation(Valid.class), "the method carries @Valid");
        assertTrue(hasValid(placeOrder.getParameterAnnotations()[0]),
                "the parameter carries @Valid");
    }

    @Test
    void anOrderThatBreaksTheSchemaIsReported() throws Exception {
        OrderRequestType order = validOrder();
        assertTrue(violations(order).isEmpty(), violations(order).toString());

        order.setCode("not a code");
        order.getCustomer().setEmail("not an address");

        List<String> messages = violations(order);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("code ")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("customer.email ")),
                messages.toString());
    }

    private static OrderRequestType validOrder() {
        CustomerType customer = new CustomerType();
        customer.setName("Acme Ltd");
        customer.setEmail("orders@acme.test");

        OrderRequestType order = new OrderRequestType();
        order.setCode("AB12345");
        order.setQuantity(3);
        order.setExpress(false);
        order.setCustomer(customer);
        return order;
    }

    private static List<String> violations(Object instance) {
        Set<ConstraintViolation<Object>> found = VALIDATOR.validate(instance);
        List<String> messages = new ArrayList<>();
        for (ConstraintViolation<Object> violation : found) {
            messages.add(violation.getPropertyPath() + " " + violation.getMessage());
        }
        return messages;
    }

    private static <A extends Annotation> A annotation(String field, Class<A> wanted)
            throws Exception {
        return OrderRequestType.class.getDeclaredField(field).getAnnotation(wanted);
    }

    private static boolean hasValid(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == Valid.class) {
                return true;
            }
        }
        return false;
    }
}
