package com.github.vh.maritima;

import com.github.vh.maritima.Maritima;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class MaritimaTest {

    @Test
    public void justRun() {
        Maritima.build()
                .start();
    }
}
