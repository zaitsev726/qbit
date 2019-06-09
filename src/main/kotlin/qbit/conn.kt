package qbit

import qbit.EInstance.entitiesCount
import qbit.EInstance.forks
import qbit.ns.Namespace
import qbit.schema.Attr
import qbit.storage.NodesStorage
import qbit.storage.Storage
import java.util.*
import java.util.Collections.singleton
import java.util.Collections.singletonList
import kotlin.ConcurrentModificationException

fun qbit(storage: Storage): LocalConn {
    val iid = IID(1, 4)
    val dbUuid = DbUuid(iid)
    val headHash = storage.load(Namespace("refs")["head"])
    if (headHash != null) {
        val head = NodesStorage(storage).load(NodeRef(Hash(headHash)))
                ?: throw QBitException("Corrupted head: no such node")
        // TODO: fix dbUuid retrieving
        return LocalConn(dbUuid, storage, head)
    }

    var eid = 0
    val trx = mutableListOf(Fact(EID(iid.value, eid), EAttr.name, EAttr.name.str()),
            Fact(EID(iid.value, eid), EAttr.type, QString.code),
            Fact(EID(iid.value, eid), EAttr.unique, true))
    eid++
    trx += listOf(Fact(EID(iid.value, eid), EAttr.name, EAttr.type.str()),
            Fact(EID(iid.value, eid), EAttr.type, QByte.code),
            Fact(EID(iid.value, eid), EAttr.unique, false))
    eid++
    trx += listOf(Fact(EID(iid.value, eid), EAttr.name, EAttr.unique.str()),
            Fact(EID(iid.value, eid), EAttr.type, QBoolean.code),
            Fact(EID(iid.value, eid), EAttr.unique, false))
    eid++
    trx += listOf(Fact(EID(iid.value, eid), EAttr.name, qbit.EAttr.list.str()),
            Fact(EID(iid.value, eid), EAttr.type, QBoolean.code),
            Fact(EID(iid.value, eid), EAttr.unique, false))
    eid++
    trx += listOf(Fact(EID(iid.value, eid), EAttr.name, forks.str()),
            Fact(EID(iid.value, eid), EAttr.type, forks.type.code),
            Fact(EID(iid.value, eid), EAttr.unique, forks.unique))
    eid++
    trx += listOf(Fact(EID(iid.value, eid), EAttr.name, entitiesCount.str()),
            Fact(EID(iid.value, eid), EAttr.type, entitiesCount.type.code),
            Fact(EID(iid.value, eid), EAttr.unique, entitiesCount.unique))
    eid++
    trx += listOf(Fact(EID(iid.value, eid), EAttr.name, EInstance.iid.str()),
            Fact(EID(iid.value, eid), EAttr.type, EInstance.iid.type.code),
            Fact(EID(iid.value, eid), EAttr.unique, EInstance.iid.unique))
    eid++
    trx += listOf(Fact(EID(iid.value, eid), EAttr.name, tombstone.str()),
            Fact(EID(iid.value, eid), EAttr.type, tombstone.type.code),
            Fact(EID(iid.value, eid), EAttr.unique, tombstone.unique))
    eid++
    trx += listOf(
            Fact(EID(iid.value, eid), EInstance.iid, 0),
            Fact(EID(iid.value, eid), forks, 0),
            Fact(EID(iid.value, eid), entitiesCount, eid + 1)) // + 1 - is current (instance) entity

    val root = Root(null, dbUuid, System.currentTimeMillis(), NodeData(trx.toTypedArray()))
    val storedRoot = NodesStorage(storage).store(root)
    storage.add(Namespace("refs")["head"], storedRoot.hash.bytes)
    return LocalConn(dbUuid, storage, storedRoot)
}

interface Conn {

    val dbUuid: DbUuid

    var head: NodeVal<Hash>

}

class LocalConn(override val dbUuid: DbUuid, val storage: Storage, override var head: NodeVal<Hash>) : Conn {

    private val nodesStorage = NodesStorage(storage)
    internal val graph = Graph(nodesStorage)
    internal val writer = Writer(nodesStorage, dbUuid)

    var db = IndexDb(Index(graph, head), head.hash)

    /**
     * Instance descriptor eid
     * Instance descriptor: {
     *   forks - count of forks created by this instance,
     *   entities - count of entities created by this instance
     * }
     */
    internal val instanceEid =
            db.query(hasAttr(entitiesCount))
                    .first { it.eid.iid == dbUuid.iid.value }
                    .eid

    internal fun swapHead(oldHead: NodeVal<Hash>, newHead: NodeVal<Hash>) {
        if (head != oldHead) {
            // todo: implement merges
            throw QBitException("Concurrent modification")
        }
        head = newHead
        db = db(graph, db, newHead)

        storage.overwrite(Namespace("refs")["head"], newHead.hash.bytes)
    }

    fun persist(e: Entitiable<*>): WriteResult {
        return persist(singleton(e))
    }

    fun persist(vararg es: Entitiable<*>): WriteResult =
            persist(es.asList())

    fun persist(es: Collection<Entitiable<*>>): WriteResult {
        val trx = trx()
        val res = trx.persist(es)
        trx.commit()
        return res
    }

    fun delete(vararg eids: EID) {
        delete(eids.asList())
    }

    fun delete(eids: Collection<EID>) {
        val trx = trx()
        trx.delete(eids)
        trx.commit()
    }

    fun trx() =
            QbitTrx(this, DbState(head, db))

}

class WriteResult(val db: Db, val persistedEntities: List<StoredEntity>, val createdEntities: Map<Entitiable<*>, StoredEntity>) {

    operator fun component1(): Db = db

    operator fun component2(): Map<Entitiable<*>, StoredEntity> = createdEntities

    operator fun component3(): StoredEntity = persistedEntities[0]

    operator fun component4(): StoredEntity = persistedEntities[1]

    operator fun component5(): StoredEntity = persistedEntities[2]

    operator fun component6(): StoredEntity = persistedEntities[3]

    operator fun component7(): StoredEntity = persistedEntities[4]

    operator fun component8(): StoredEntity = persistedEntities[5]

    fun storedEntity(): StoredEntity =
            persistedEntities[0]

}

class QbitTrx internal constructor(val conn: LocalConn, private val base: DbState, private var state: DbState? = null) {

    val db
        get() = (this.state ?: this.base).db

    private val baseState
        get() = this.state ?: this.base

    fun persist(e: Entitiable<*>): WriteResult {
        return persist(singleton(e))
    }

    fun persist(vararg es: Entitiable<*>): WriteResult =
            persist(es.asList())

    fun persist(es: Collection<Entitiable<*>>): WriteResult {
        val (res, newState) = persistImpl(es)
        this.state = newState
        return res
    }

    fun delete(eids: Collection<EID>) {
        this.state = deleteImpl(eids)
    }

    fun commit() {
        val lstate = state
        if (lstate != null) {
            conn.swapHead(base.head, lstate.head)
            state = DbState(conn.head, conn.db)
        }
    }

    fun rollback() {
        state = null
        // todo: delete nodes up to conn.head
    }

    private fun persistImpl(es: Collection<Entitiable<*>>): Pair<WriteResult, DbState> {
        if (conn.head != base.head) {
            throw ConcurrentModificationException("Another modification has been committed")
        }
        try {
            // TODO: now persistence of entity with all attr removes is noop

            val db = this.db

            val instance = db.pull(conn.instanceEid) ?: throw QBitException("Corrupted database metadata")
            val eids = EID(conn.dbUuid.iid.value, instance[entitiesCount]).nextEids()

            val allEs: IdentityHashMap<Entitiable<*>, Entity<EID>> = unfoldEntitiesGraph(es, eids)
            val facts: MutableList<Fact> = entitiesToFacts(allEs, eids, instance)
            if (facts.isEmpty()) {
                assert { es.all { it is StoredEntity && !it.dirty } }
                return WriteResult(db, es.filterIsInstance<StoredEntity>(), emptyMap()) to DbState(baseState.head, db)
            }
            val newAttrs = es.filterIsInstance<Attr<*>>()
            validate(baseState.db, facts, newAttrs)
            val newHead = persistFacts(facts)
            val newDb = db(conn.graph, baseState.db, newHead)

            val persistedEntities = es
                    .map { Entity(allEs[it]!!.eid, newDb) }.toList()
            val createdEntities = allEs.filterKeys { it !is StoredEntity }.mapValues { Entity(it.value.eid, newDb) }

            return WriteResult(db, persistedEntities, createdEntities) to DbState(newHead, newDb)

        } catch (qe: QBitException) {
            throw qe
        } catch (e: Exception) {
            throw QBitException(cause = e)
        }
    }

    private fun unfoldEntitiesGraph(es: Collection<Entitiable<*>>, eids: Iterator<EID>): IdentityHashMap<Entitiable<*>, Entity<EID>> {
        val res = IdentityHashMap<Entitiable<*>, Entity<EID>>()

        fun body(es: Collection<Entitiable<*>>) {
            es.forEach {
                if (!res.contains(it)) {
                    when (it) {
                        is Entity -> {
                            res[it] = if (it.eid != null) it as Entity<EID> else it.toIdentified(eids.next())
                        }
                        else -> res[it] = Entity(*it.entries.toTypedArray()).toIdentified(eids.next())
                    }
                }
                it.entries.forEach { e ->
                    if (e is ScalarRefAttrValue) {
                        val value: Entity<*>? = e.value
                        if (value != null && res[value] == null) {
                            body(singletonList(value))
                        }
                    } else if (e is RefListAttrValue) {
                        body(e.value)
                    }
                }
            }
        }
        body(es)

        return res
    }

    private fun entitiesToFacts(allEs: IdentityHashMap<Entitiable<*>, Entity<EID>>, eids: Iterator<EID>, instance: StoredEntity): MutableList<Fact> {
        val entitiesToStore: List<Entity<EID>> = allEs.values.filter { it !is StoredEntity || it.dirty }
        val linkedEntities: List<Entity<EID>> = entitiesToStore.map { it.setRefs(allEs) }
        val facts: MutableList<Fact> = linkedEntities
                .flatMap { it.toFacts() }
                .toMutableList()
        val newEntitiesCnt = eids.next().eid
        if (instance[entitiesCount] < newEntitiesCnt) {
            facts += instance.set(entitiesCount, newEntitiesCnt).toFacts()
        }
        return facts
    }

    private fun deleteImpl(eids: Collection<EID>): DbState {
        val tombstones = eids.map { Fact(it, tombstone, true) }
        val newHead = persistFacts(tombstones)
        return DbState(newHead, db(conn.graph, baseState.db, newHead))
    }

    private fun persistFacts(facts: Collection<Fact>): NodeVal<Hash> {
        return conn.writer.store(baseState.head, facts)
    }

}

internal class DbState(val head: NodeVal<Hash>, val db: IndexDb)

internal fun db(graph: Graph, oldDb: IndexDb, newHead: NodeVal<Hash>): IndexDb {
    return if (newHead is Merge) {
        IndexDb(Index(graph, newHead), newHead.hash)
    } else {
        fun nodesTo(n: NodeVal<Hash>, target: Hash): List<NodeVal<Hash>> {
            return when {
                n.hash == target -> emptyList()
                n is Leaf -> nodesTo(graph.resolveNode(n.parent), target) + n
                n is Merge -> throw UnsupportedOperationException("Merges not yet supported")
                else -> {
                    check(n is Root)
                    throw AssertionError("Should never happen")
                }
            }
        }
        val nodes = nodesTo(graph.resolveNode(newHead), oldDb.hash)
        return nodes.fold(oldDb) { db, n ->
            val entities = n.data.trx.toList()
                    .groupBy { it.eid }
                    .map { it.key to it.value }
            IndexDb(db.index.add(entities), n.hash)
        }
    }
}
