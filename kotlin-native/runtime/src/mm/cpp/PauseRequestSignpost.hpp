/*
 * Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include "Utils.hpp"

namespace kotlin::mm {

class PauseRequestSignpost : private MoveOnly {
public:
    static std::optional<PauseRequestSignpost> create(const char* reason) noexcept;

    PauseRequestSignpost(PauseRequestSignpost&& rhs) noexcept : id_(rhs.id_) { rhs.id_ = 0; }

    PauseRequestSignpost& operator=(PauseRequestSignpost&& rhs) noexcept {
        PauseRequestSignpost tmp(std::move(rhs));
        swap(*this, tmp);
        return *this;
    }

    ~PauseRequestSignpost();

    friend void swap(PauseRequestSignpost& lhs, PauseRequestSignpost& rhs) noexcept {
        using std::swap;
        swap(lhs.id_, rhs.id_);
    }

private:
    explicit PauseRequestSignpost(const char* reason) noexcept;

    uint64_t id_ = 0;
};

} // namespace kotlin::mm