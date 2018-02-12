package qbit

import qbit.schema.Attr
import qbit.schema.Schema
import qbit.schema.parseAttrName

class Db(val head: NodeVal<Hash>, resolve: (NodeRef) -> NodeVal<Hash>?) {

    private val graph = Graph(resolve)
    private val index = createIndex(graph, head)
    val schema = Schema(loadAttrs(index))

    companion object {
        fun createIndex(graph: Graph, head: NodeVal<Hash>): Index {
            val parentIdx =
                    when (head) {
                        is Root -> Index()
                        is Leaf -> createIndex(graph, graph.resolve(head.parent))
                        is Merge -> {
                            val idx1 = createIndex(graph, graph.resolve(head.parent1))
                            val idx2 = createIndex(graph, graph.resolve(head.parent2))
                            idx2.add(idx1.eavt.filterIsInstance<StoredFact>().toList())
                        }
                    }
            return parentIdx.add(head.data.trx.map { StoredFact(it.eid, it.attr, head.timestamp, it.value) })
        }

        private fun Graph.resolve(n: Node<Hash>) = when (n) {
            is NodeVal<Hash> -> n
            is NodeRef -> this.resolve(n) ?: throw QBitException("Corrupted graph, could not resolve $n")
        }


        private fun loadAttrs(index: Index): List<Attr<*>> {
            val factsByAttr: List<StoredFact> = index.factsByAttr(qbit.schema._name.str)
            return factsByAttr
                    .map {
                        val e = index.factsByEid(it.eid)!!
                        val name = e[qbit.schema._name.str]!! as String
                        val type = e[qbit.schema._type.str]!! as Byte
                        val unique = e[qbit.schema._unique.str] as? Boolean ?: false
                        Attr(qbit.schema.parseAttrName(name), DataType.ofCode(type)!!, unique)
                    }
        }
    }

    fun pull(eid: EID): StoredEntity? = index.entityById(eid)?.let { MapEntity(eid, it.mapKeys { schema.find(parseAttrName(it.key))!! }) }

    fun <T : Any> entitiesByAttr(attr: Attr<T>, value: T? = null): List<StoredEntity> {
        val eids =
                if (value != null) index.entitiesByAttrVal(attr.str, value)
                else index.entitiesByAttr(attr.str)

        return eids.map { pull(it)!! }
    }

    fun findSubgraph(uuid: DbUuid): Node<Hash> {
        return graph.findSubgraph(head, uuid)
    }

    /**
     * sgRoot - root of the subgraph
     */
    fun findSubgraph(n: Node<Hash>, sgRootsHashes: Set<Hash>): Node<Hash> = when {
        n is Leaf && n.parent.hash in sgRootsHashes -> Leaf(n.hash, NodeRef(n.parent), n.source, n.timestamp, n.data)
        n is Leaf && n.parent.hash !in sgRootsHashes -> findSubgraph(n.parent, sgRootsHashes)
        n is Merge -> Merge(n.hash, findSubgraph(n.parent1, sgRootsHashes), findSubgraph(n.parent2, sgRootsHashes), n.source, n.timestamp, n.data)
        else -> throw AssertionError("Should never happen, n is $n root is $sgRootsHashes")
    }

}

private class MapEntity(
        override val eid: EID,
        private val map: Map<Attr<*>, Any>
) :
        StoredEntity,
        Map<Attr<*>, Any> by map