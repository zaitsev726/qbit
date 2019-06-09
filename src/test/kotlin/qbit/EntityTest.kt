package qbit

import org.junit.Assert.*
import org.junit.Test
import qbit.ns.Namespace
import qbit.ns.root
import qbit.schema.*

@Suppress("UNCHECKED_CAST")
class EntityTest {

    @Test
    fun testCreate() {
        val user = Namespace("user")
        val _attr = ScalarAttr(user["attr"], QString)
        val _list = ListAttr(user["list"], QString)
        val _ref = RefAttr(user["ref"])
        val _refList = RefListAttr((user["refList"]))
        val _eid = ScalarAttr(user["some_eid"], QEID)

        val e1 = Entity(_attr eq "e1")
        assertTrue((e1 as MapEntity).refs.isEmpty())
        val e2 = Entity(_attr eq "e2", _ref eq e1, _eid eq EID(0, 3), _list eq listOf("one", "two"),
                _refList eq listOf(e1))
        assertTrue((e2 as MapEntity).map.size == 3)
        assertTrue(e2.refs.size == 2)

        assertEquals("e2", e2[_attr])
        assertEquals(EID(0, 3), e2[_eid])
        assertTrue(e2[_ref] === e1)
        assertArrayEquals(arrayOf("one", "two"), e2[_list].toTypedArray())
        val list: List<Entity<*>> = e2[_refList]
        val array: Array<Entity<*>> = list.toTypedArray()
        assertArrayEquals(arrayOf(e1), list.toTypedArray())
        assertEquals(5, e2.entries.size)
    }

    @Test
    fun testPutRef() {
        var first = Entity()
        val second = Entity()
        val attr = RefAttr(root["test"])
        first = first.set(attr, second)
        assertTrue(second === first[attr])
    }

    @Test
    fun testListAttr() {
        var e = Entity()
        val attr = ListAttr(root["test"], QString)
        e = e.set(attr, listOf("first"))
        assertEquals(listOf("first"), e[attr])
    }

    @Test
    fun testSetAttrs() {
        var e = Entity()
        val _first = ScalarAttr(root["first"], QLong)
        val _second = RefAttr(root["second"])
        val _third = ListAttr(root["third"], QString)
        val referee = Entity()
        e = e.set(_first eq 1,
                _second eq referee,
                _third eq listOf("3"))
        assertEquals(1, e[_first])
        assertEquals(referee, e[_second])
        assertEquals(listOf("3"), e[_third])

    }

    @Test
    fun testUpdateRef() {
        val s = ScalarAttr(root["scalar"], QString)
        val r1 = Entity(s eq "s1").toIdentified(EID(1))
        val r2 = Entity(s eq "s2").toIdentified(EID(2))
        val ref = RefAttr(root["ref"])
        var e: StoredEntity = StoredMapEntity(EID(0), mapOf(ref to r1) as MutableMap<Attr<*>, Any>,
                hashMapOf(), { null }, false)
        e = e.set(ref, r2)
        assertEquals(1, e.toFacts().size)
    }

    @Test
    fun testSetStoredEntityVarargWithPartialUpdate() {
        val s1 = ScalarAttr(root["scalar1"], QString)
        val s2 = ScalarAttr(root["scalar2"], QString)
        val e: StoredEntity = StoredMapEntity(EID(0), mapOf(s1 to "value1", s2 to "value2") as MutableMap<Attr<*>, Any>,
                hashMapOf(), { null }, false)
        val ne = e.set(s1 eq "value1", s2 eq "value3")
        assertNotEquals(ne, e)
        assertEquals("value3", ne[s2])
    }

    @Test
    fun testUpdateRefList() {
        val rl = RefListAttr(root["refList"])
        var e: StoredEntity = StoredMapEntity(EID(0), mapOf(rl to listOf(EID(1), EID(2))) as MutableMap<Attr<*>, Any>, hashMapOf(), { null }, false)
        e = e.set(rl, listOf(Entity()))
        assertEquals(1, e.entries.size)
    }

    @Test
    fun testUpdateRefViaVararg() {
        val s = ScalarAttr(root["scalar"], QString)
        val ref = RefAttr(root["refList"])
        var e: StoredEntity = StoredMapEntity(EID(0), mapOf(s to "any", ref to EID(1)) as MutableMap<Attr<*>, Any>, hashMapOf(), { null }, false)
        e = e.set(ref eq Entity(), s eq "newAny")
        assertEquals(2, e.entries.size)
    }

    @Test
    fun testUpdateRefListViaVararg() {
        val s = ScalarAttr(root["scalar"], QString)
        val rl = RefListAttr(root["refList"])
        var e: StoredEntity = StoredMapEntity(EID(0), mapOf(s to "any", rl to listOf(EID(1), EID(2))) as MutableMap<Attr<*>, Any>, hashMapOf(), { null }, false)
        e = e.set(rl eq listOf(Entity()), s eq "newAny")
        assertEquals(2, e.entries.size)
    }

}