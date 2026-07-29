// This specific file is licensed CC-0/Unlicense/whatever is most permissive.
// The reason: it was pretty much fully AI-generated with a few manual fixes.
// I just couldn't be bothered to make an adaptive Trie like this myself for such a small project.

package com.martmists.opensearch.stubs.util

class AdaptiveTrie<V>(
    private val maxDepth: Int = 8,
    private val maxLeafSize: Int = 16,
    private val ignoreCase: Boolean = true
) {
    init {
        require(maxDepth >= 0) { "maxDepth must be non-negative" }
        require(maxLeafSize > 0) { "maxLeafSize must be greater than zero" }
    }

    private data class Entry<V>(
        val originalKey: String,
        val normalizedKey: String,
        val value: V
    )

    private sealed class Node<V> {
        class Leaf<V>(val entries: MutableList<Entry<V>> = mutableListOf()) : Node<V>()

        class Internal<V>(
            val children: MutableMap<Char, Node<V>> = mutableMapOf(),
            val exactTerminalEntries: MutableList<Entry<V>> = mutableListOf()
        ) : Node<V>()
    }

    private var root: Node<V> = Node.Leaf()

    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        var lastWasSpace = false

        for (ch in text) {
            val isAlphaNum = ch.isLetterOrDigit()
            if (isAlphaNum) {
                val targetChar = if (ignoreCase) ch.lowercaseChar() else ch
                sb.append(targetChar)
                lastWasSpace = false
            } else {
                if (!lastWasSpace && sb.isNotEmpty()) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            }
        }

        if (sb.isNotEmpty() && sb.last() == ' ') {
            sb.deleteAt(sb.length - 1)
        }

        return sb.toString()
    }

    fun insert(key: String, value: V) {
        val normalized = normalize(key)
        val entry = Entry(key, normalized, value)
        root = insertIntoNode(root, entry, depth = 0)
    }

    private fun insertIntoNode(node: Node<V>, entry: Entry<V>, depth: Int): Node<V> {
        return when (node) {
            is Node.Leaf -> {
                val existingIndex = node.entries.indexOfFirst { it.normalizedKey == entry.normalizedKey }
                if (existingIndex != -1) {
                    node.entries[existingIndex] = entry
                    return node
                }

                if (node.entries.size >= maxLeafSize && depth < maxDepth) {
                    val internalNode = Node.Internal<V>()
                    for (existingEntry in node.entries) {
                        routeIntoInternal(internalNode, existingEntry, depth)
                    }
                    routeIntoInternal(internalNode, entry, depth)
                    internalNode
                } else {
                    node.entries.add(entry)
                    node
                }
            }

            is Node.Internal -> {
                routeIntoInternal(node, entry, depth)
                node
            }
        }
    }

    private fun routeIntoInternal(internal: Node.Internal<V>, entry: Entry<V>, depth: Int) {
        if (depth == entry.normalizedKey.length) {
            val existingIndex = internal.exactTerminalEntries.indexOfFirst { it.normalizedKey == entry.normalizedKey }
            if (existingIndex != -1) {
                internal.exactTerminalEntries[existingIndex] = entry
            } else {
                internal.exactTerminalEntries.add(entry)
            }
            return
        }

        val char = entry.normalizedKey[depth]
        val child = internal.children.getOrPut(char) { Node.Leaf() }
        internal.children[char] = insertIntoNode(child, entry, depth + 1)
    }

    fun searchByPrefix(prefix: String): List<V> {
        val normalizedPrefix = normalize(prefix)
        var current = root
        var depth = 0

        while (depth < normalizedPrefix.length) {
            when (val node = current) {
                is Node.Leaf -> {
                    return node.entries
                        .filter { it.normalizedKey.startsWith(normalizedPrefix) }
                        .map { it.value }
                }

                is Node.Internal -> {
                    val char = normalizedPrefix[depth]
                    val nextChild = node.children[char] ?: return emptyList()
                    current = nextChild
                    depth++
                }
            }
        }

        val results = mutableListOf<V>()
        collectAll(current, results)
        return results
    }

    private fun collectAll(node: Node<V>, results: MutableList<V>) {
        when (node) {
            is Node.Leaf -> {
                results.addAll(node.entries.map { it.value })
            }

            is Node.Internal -> {
                results.addAll(node.exactTerminalEntries.map { it.value })
                for (child in node.children.values) {
                    collectAll(child, results)
                }
            }
        }
    }
}
