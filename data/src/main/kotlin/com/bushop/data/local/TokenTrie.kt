package com.bushop.data.local

internal class TokenTrie {
    private class Node {
        val children = mutableMapOf<Char, Node>()
        var indices: List<Int>? = null
    }

    private val root = Node()

    fun insert(
        token: String,
        index: Int,
    ) {
        var node = root
        for (c in token) {
            node = node.children.getOrPut(c) { Node() }
        }
        node.indices = (node.indices ?: emptyList()) + index
    }

    /** All indices whose token starts with [prefix]. */
    fun findByPrefix(prefix: String): List<Int> {
        var node = root
        for (c in prefix) {
            node = node.children[c] ?: return emptyList()
        }
        return collectIndices(node)
    }

    /** All indices for tokens that are prefixes of [query] (reverse prefix). */
    fun findPrefixesOf(query: String): List<Int> {
        val result = mutableListOf<Int>()
        var node = root
        for (c in query) {
            node = node.children[c] ?: break
            node.indices?.let { result.addAll(it) }
        }
        return result
    }

    private fun collectIndices(node: Node): List<Int> {
        val result = mutableListOf<Int>()
        val stack = ArrayDeque<Node>().apply { add(node) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            n.indices?.let { result.addAll(it) }
            stack.addAll(n.children.values)
        }
        return result
    }
}
