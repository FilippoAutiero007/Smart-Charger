package com.example

import com.example.ui.navigation.Screen
import org.junit.Test

class ScreenTest {
    @Test
    fun testScreenItems() {
        println("Screen items: ${Screen.items}")
        for (item in Screen.items) {
            println("Item: $item, title: ${item?.title}")
        }
    }
}
