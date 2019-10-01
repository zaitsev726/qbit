package qbit

import qbit.model.Attr
import qbit.model.IID
import qbit.ns.Namespace
import qbit.storage.MemStorage
import qbit.trx.Instance
import qbit.trx.bootstrap
import qbit.trx.qbit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue



class BootstrapTest {

    private val storage = MemStorage()
    private val newDb = bootstrap(DbUuid(IID(1, 4)), storage)

    @Test
    fun testInit() {
        val db = qbit(storage)
        assertNotNull(db)
        assertTrue(storage.keys(Namespace("nodes")).isNotEmpty())
    }

    @Test
    fun `Attr attrs is correctly bootstrapped`() {
        assertEquals(Attrs.name.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, Attrs.name.name)).first().name)
        assertEquals(Attrs.type.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, Attrs.type.name)).first().name)
        assertEquals(Attrs.unique.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, Attrs.unique.name)).first().name)
        assertEquals(Attrs.list.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, Attrs.list.name)).first().name)
    }

    @Test
    fun `Instance attrs is correctly bootstrapped`() {
        assertEquals(Instances.iid.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, Instances.iid.name)).first().name)
        assertEquals(Instances.forks.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, Instances.forks.name)).first().name)
        assertEquals(Instances.nextEid.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, Instances.nextEid.name)).first().name)
    }

    @Test
    fun `Tombstone attr is correctly bootstrapped`() {
        assertEquals(tombstone.name, newDb.db().queryT<Attr<*>>(attrIs(Attrs.name, tombstone.name)).first().name)
    }

    @Test
    fun `The Instance's next eid value is correctly bootstrapped`() {
        val theInstance = newDb.db().queryT<Instance>(attrIs(Instances.iid, 1)).first()
        val actualMaxEidValue = newDb.db().queryGids().map { it.eid }.max()!!
        assertTrue(theInstance.nextEid > actualMaxEidValue, "Next eid value ${theInstance.nextEid} should be greater, than actual max eid value $actualMaxEidValue")
    }

}