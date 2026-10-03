package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HorizontalOrderTest {
    @Test void rectangularEdgesHaveEqualPriorityAndCenterComesFirst() {
        assertEquals(0, HorizontalOrder.fraction(0, 0, -100, -10, 100, 10));
        assertEquals(1, HorizontalOrder.fraction(100, 0, -100, -10, 100, 10));
        assertEquals(1, HorizontalOrder.fraction(0, 10, -100, -10, 100, 10));
        assertEquals(.5, HorizontalOrder.fraction(50, 0, -100, -10, 100, 10));
    }
    @Test void reversedCornersAndSingleColumnAreValid() {
        assertEquals(.5, HorizontalOrder.fraction(50, 0, 100, 10, -100, -10));
        assertEquals(0, HorizontalOrder.fraction(5, 5, 5, 5, 5, 5));
    }
}
