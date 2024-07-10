/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_GCAPI_HPP_
#define CUSTOM_ALLOC_CPP_GCAPI_HPP_

#include <cinttypes>
#include <cstdint>
#include <cstdlib>
#include <limits>

#include "Alignment.hpp"
#include "AllocationSize.hpp"
#include "AtomicStack.hpp"
#include "ExtraObjectData.hpp"
#include "GC.hpp"
#include "GCStatistics.hpp"
#include "Memory.h"
#include "TypeLayout.hpp"
#include "CustomFinalizerProcessor.hpp"

namespace kotlin::alloc {

struct HeapObjHeader {
    using descriptor = type_layout::Composite<HeapObjHeader, gc::GC::ObjectData, ObjHeader>;

    static HeapObjHeader& from(gc::GC::ObjectData& objectData) noexcept { return *descriptor().fromField<0>(&objectData); }

    static HeapObjHeader& from(ObjHeader* object) noexcept {
        RuntimeAssert(object->heap(), "Object %p does not reside in the heap", object);
        return *descriptor().fromField<1>(object);
    }

    gc::GC::ObjectData& objectData() noexcept { return *descriptor().field<0>(this).second; }

    ObjHeader* object() noexcept { return descriptor().field<1>(this).second; }

private:
    HeapObjHeader() = delete;
    ~HeapObjHeader() = delete;
};

struct HeapObject {
    using descriptor = type_layout::Composite<HeapObject, HeapObjHeader, ObjectBody>;

    static descriptor make_descriptor(const TypeInfo* typeInfo) noexcept {
        return descriptor{{}, type_layout::descriptor_t<ObjectBody>{typeInfo}};
    }

    HeapObjHeader& header(descriptor descriptor) noexcept { return *descriptor.field<0>(this).second; }

private:
    HeapObject() = delete;
    ~HeapObject() = delete;
};

// Needs to be kept compatible with `HeapObjHeader` just like `ArrayHeader` is compatible
// with `ObjHeader`: the former can always be casted to the other.
struct HeapArrayHeader {
    using descriptor = type_layout::Composite<HeapArrayHeader, gc::GC::ObjectData, ArrayHeader>;

    static HeapArrayHeader& from(gc::GC::ObjectData& objectData) noexcept { return *descriptor().fromField<0>(&objectData); }

    static HeapArrayHeader& from(ArrayHeader* array) noexcept { return *descriptor().fromField<1>(array); }

    gc::GC::ObjectData& objectData() noexcept { return *descriptor().field<0>(this).second; }

    ArrayHeader* array() noexcept { return descriptor().field<1>(this).second; }

private:
    HeapArrayHeader() = delete;
    ~HeapArrayHeader() = delete;
};

struct HeapArray {
    using descriptor = type_layout::Composite<HeapArray, HeapArrayHeader, ArrayBody>;

    static descriptor make_descriptor(const TypeInfo* typeInfo, uint32_t size) noexcept {
        return descriptor{{}, type_layout::descriptor_t<ArrayBody>{typeInfo, size}};
    }

    HeapArrayHeader& header(descriptor descriptor) noexcept { return *descriptor.field<0>(this).second; }

private:
    HeapArray() = delete;
    ~HeapArray() = delete;
};

struct ObjectSweepTraits {
    using GCSweepScope = gc::GCHandle::GCSweepScope;

    static GCSweepScope currentGCSweepScope(gc::GCHandle& handle) noexcept { return handle.sweep(); }

    static bool trySweepElement(uint8_t* data, FinalizerQueue& finalizerQueue, GCSweepScope& sweepScope) noexcept;

    static AllocationSize elementSize(uint8_t* data);
};

struct ExtraDataSweepTraits {
    using GCSweepScope = gc::GCHandle::GCSweepExtraObjectsScope;

    static GCSweepScope currentGCSweepScope(gc::GCHandle& handle) noexcept { return handle.sweepExtraObjects(); }

    static bool trySweepElement(uint8_t* data, FinalizerQueue& finalizerQueue, GCSweepScope& sweepScope) noexcept;

    static AllocationSize elementSize(uint8_t*);
};

void* SafeAlloc(uint64_t size) noexcept;

void Free(void* ptr, size_t size) noexcept;

size_t GetAllocatedBytes() noexcept;

} // namespace kotlin::alloc

#endif
