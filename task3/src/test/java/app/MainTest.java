package app;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @Test
    void testParseArgs_EmptyArray_ReturnsDefaultPort() {
        String[] args = new String[]{};
        Main.Config config = Main.parseArgs(args);
        assertEquals(8080, config.port(),
                "При пустом массиве аргументов должен использоваться порт 8080");
    }

    @Test
    void testParseArgs_ValidPort_ReturnsSpecifiedPort() {
        String[] args = new String[]{"3000"};
        Main.Config config = Main.parseArgs(args);
        assertEquals(3000, config.port(),
                "Должен примениться переданный порт 3000");
    }

    @Test
    void testParseArgs_InvalidPort_ReturnsDefaultPort() {
        String[] args = new String[]{"abc"};
        Main.Config config = Main.parseArgs(args);
        assertEquals(8080, config.port(),
                "При ошибке NumberFormatException должен использоваться порт 8080");
    }
}
