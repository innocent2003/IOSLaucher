package com.example.oslaucher.models

internal object AppGridOrder {
    fun <T> orderItems(
        items: List<T>,
        orderedKeys: List<String?>,
        keyOf: (T) -> String
    ): List<T?> {
        if (orderedKeys.isEmpty()) return items

        val itemsByKey = items.associateBy(keyOf)
        val ordered = mutableSetOf<String>()
        val result = mutableListOf<T?>()
        orderedKeys.forEach { packageName ->
            when {
                packageName.isNullOrBlank() -> result.add(null)
                !ordered.add(packageName) -> result.add(null)
                else -> result.add(itemsByKey[packageName])
            }
        }
        result.addAll(items.filterNot { keyOf(it) in ordered })
        return result
    }

    fun moveApp(
        packages: List<String?>,
        packageName: String,
        pageIndex: Int,
        pagePosition: Int?,
        pageSize: Int,
        dropAfterTarget: Boolean
    ): List<String?>? {
        val sourceIndex = packages.indexOf(packageName)
        if (sourceIndex < 0) return null

        val pageStart = pageIndex * pageSize
        val pageEnd = minOf(pageStart + pageSize, packages.size)
        val directTargetIndex = pagePosition?.let { pageStart + it } ?: -1
        val targetIsEmpty = directTargetIndex >= 0 &&
            packages.getOrNull(directTargetIndex) == null
        val targetIndex = if (pagePosition == null) {
            pageEnd.coerceAtMost(pageStart + pageSize - 1)
        } else if (targetIsEmpty) {
            directTargetIndex
        } else {
            (pageStart + pagePosition + if (dropAfterTarget) 1 else 0)
                .coerceIn(pageStart, pageStart + pageSize - 1)
        }
        val reordered = packages.toMutableList().apply {
            while (size <= maxOf(sourceIndex, targetIndex)) add(null)
        }
        if (targetIndex == sourceIndex) return null

        val targetPackage = reordered[targetIndex]
        if (targetPackage == null) {
            reordered[sourceIndex] = null
            reordered[targetIndex] = packageName
        } else {
            reordered[sourceIndex] = null
            reordered.add(targetIndex, packageName)
        }
        return reordered
    }
}
