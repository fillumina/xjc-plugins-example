package com.fillumina.xjc.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.order.CustomerType;
import com.example.order.ObjectFactory;
import com.example.order.OrderRequestType;
import com.example.order.OrderServicePortType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.beans.Introspector;
import java.io.StringReader;
import java.io.StringWriter;
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
        assertNotNull(annotation("quantity", NotNull.class));
        assertNotNull(annotation("express", NotNull.class));
    }

    @Test
    void theBoxedBooleanIsAReadableBeanPropertyWithoutChangingJaxbFieldBinding() throws Exception {
        assertEquals(XmlAccessType.FIELD,
                OrderRequestType.class.getAnnotation(XmlAccessorType.class).value());
        var express = java.util.Arrays.stream(Introspector.getBeanInfo(OrderRequestType.class)
                        .getPropertyDescriptors())
                .filter(property -> property.getName().equals("express"))
                .findFirst().orElseThrow();
        assertNotNull(express.getReadMethod());
        assertNotNull(express.getWriteMethod());
        assertEquals(Boolean.class, express.getPropertyType());
        assertEquals("getExpress", express.getReadMethod().getName());

        OrderRequestType order = validOrder();
        assertEquals(Boolean.FALSE, express.getReadMethod().invoke(order));
        order.setExpress(true);
        assertEquals(Boolean.TRUE, express.getReadMethod().invoke(order));
        assertEquals(Boolean.TRUE, order.isExpress());

        JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
        StringWriter xml = new StringWriter();
        context.createMarshaller().marshal(new ObjectFactory().createOrderRequest(order), xml);
        Object decoded = context.createUnmarshaller().unmarshal(new StringReader(xml.toString()));
        assertTrue(decoded instanceof JAXBElement<?>);
        OrderRequestType restored = (OrderRequestType) ((JAXBElement<?>) decoded).getValue();
        assertEquals(Boolean.TRUE, restored.isExpress());
        assertEquals(Boolean.TRUE, express.getReadMethod().invoke(restored));
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
    void reversingTheTwoXjcOptionsKeepsTheGeneratedContracts() throws Exception {
        // The second CXF run has a distinct package so both generated models compile in this build.
        Class<?> reversed = Class.forName("com.example.order.reversed.OrderRequestType");
        Class<?> reversedService = Class.forName("com.example.order.reversed.OrderServicePortType");
        compareFields(OrderRequestType.class, reversed,
                List.of("code", "quantity", "express", "customer", "note"));
        compareFields(CustomerType.class, Class.forName("com.example.order.reversed.CustomerType"),
                List.of("name", "email"));
        compareFields(com.example.order.OrderResponseType.class,
                Class.forName("com.example.order.reversed.OrderResponseType"),
                List.of("accepted", "reference"));
        assertEquals(OrderRequestType.class.getMethod("getExpress").getReturnType(),
                reversed.getMethod("getExpress").getReturnType());
        assertNotNull(reversedService.getMethod("placeOrder", reversed).getAnnotation(Valid.class));
        assertTrue(hasValid(reversedService.getMethod("placeOrder", reversed).getParameterAnnotations()[0]));
    }

    private static void compareFields(Class<?> first, Class<?> second, List<String> names)
            throws NoSuchFieldException {
        for (String name : names) {
            var expected = first.getDeclaredField(name);
            var actual = second.getDeclaredField(name);
            assertEquals(expected.getType().getSimpleName(), actual.getType().getSimpleName(), name);
            assertArrayEquals(expected.getAnnotations(), actual.getAnnotations(), name);
            assertArrayEquals(expected.getAnnotatedType().getAnnotations(),
                    actual.getAnnotatedType().getAnnotations(), name + " type annotations");
        }
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
