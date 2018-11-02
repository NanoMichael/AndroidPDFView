package io.ea.pdf.test

import org.junit.Test
import java.util.*

/**
 * Created by nano on 17-11-23.
 */
class KotlinLangTest {

    @Test
    fun testRemoveIterator() {
        val list = mutableListOf(1, 2, 3, 4, 5)
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val x = iterator.next()
            iterator.remove()
            System.out.println("remove: $x, remain: $list")
        }
        val x = Vector<Int>()
    }

    @Test
    fun testRemoveAll() {
        val list = mutableListOf(1, 2, 3, 4, 5)
        list.removeAll { it < 3 }
        System.out.println("remove if x < 3, $list")
    }
}
