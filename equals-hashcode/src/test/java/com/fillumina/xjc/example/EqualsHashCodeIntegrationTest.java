package com.fillumina.xjc.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.equals.SampleType;
import java.beans.Introspector;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Tests generated equality with nullable fields after primitive replacement. */
class EqualsHashCodeIntegrationTest {

    @Test
    void boxedFieldsHaveMatchingEqualityAndHashCodes() throws Exception {
        assertEquals(Integer.class, SampleType.class.getDeclaredField("count").getType());
        assertEquals(Boolean.class, SampleType.class.getDeclaredField("active").getType());
        assertNotNull(SampleType.class.getDeclaredMethod("getActive"));
        var property = Arrays.stream(Introspector.getBeanInfo(SampleType.class).getPropertyDescriptors())
                .filter(item -> item.getName().equals("active")).findFirst().orElseThrow();
        assertEquals(Boolean.class, property.getPropertyType());
        assertNotNull(property.getReadMethod());

        SampleType left = new SampleType();
        SampleType right = new SampleType();
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());

        left.setCount(0);
        assertNotEquals(left, right, "null count differs from zero");
        right.setCount(0);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());

        left.setActive(false);
        assertNotEquals(left, right, "null active differs from false");
        right.setActive(false);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());

        right.setActive(true);
        assertNotEquals(left, right);
        right.setActive(false);
        right.setLabel("changed");
        assertNotEquals(left, right);
        left.setLabel("changed");
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertTrue(left.equals(left));
    }
}
