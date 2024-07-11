/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <atomic>
#include <utility>

#include "CallsChecker.hpp"
#include "PauseRequestSignpost.hpp"
#include "ThreadRegistry.hpp"
#include "Utils.hpp"

#include <shared_mutex>

namespace kotlin::mm {

class ThreadData;

class SafePointActivator : private MoveOnly {
public:
    explicit SafePointActivator(const char* reason) noexcept;
    ~SafePointActivator();

    SafePointActivator(SafePointActivator&& rhs) noexcept :
        active_(rhs.active_), pauseRequestSignpost_(std::move(rhs.pauseRequestSignpost_)) {
        rhs.active_ = false;
    }

    SafePointActivator& operator=(SafePointActivator&& rhs) noexcept {
        SafePointActivator other(std::move(rhs));
        swap(*this, other);
        return *this;
    }

    friend void swap(SafePointActivator& lhs, SafePointActivator& rhs) noexcept {
        using std::swap;
        swap(lhs.active_, rhs.active_);
        swap(lhs.pauseRequestSignpost_, rhs.pauseRequestSignpost_);
    }

private:
    bool active_;
    std::optional<PauseRequestSignpost> pauseRequestSignpost_;
};

void safePoint(std::memory_order fastPathOrder = std::memory_order_relaxed) noexcept;
void safePoint(ThreadData& threadData, std::memory_order fastPathOrder = std::memory_order_relaxed) noexcept;

/**
 * A helper class template to implement custom safe point action activators.
 * An implementation has to inherit from this template, providing itself as a CRTP template argument.
 *
 * It's guaranteed that the action executed through `doIfActive`
 * will be fully completed before the activator destructor returns.
 */
template <typename Impl>
class ExtraSafePointActionActivator : private MoveOnly {
public:
    template <typename Action>
    static void doIfActive(Action&& action) {
        CallsCheckerIgnoreGuard guard;

        // Without this check and with many frequent-enough readers,
        // a writer might never get a chance to obtain the lock.
        if (!active_.load(std::memory_order_relaxed)) return;

        std::shared_lock lock(mutex_);
        if (active_.load(std::memory_order_relaxed)) {
            action();
        }
    }

    static bool isActive() noexcept {
        return active_.load(std::memory_order_relaxed);
    }

    explicit ExtraSafePointActionActivator(const char* reason) noexcept : safePointActivator_(reason) {
        std::unique_lock lock(mutex_);
        active_.store(true, std::memory_order_relaxed);
    }

    virtual ~ExtraSafePointActionActivator() noexcept = 0;

private:
    [[clang::no_destroy]] inline static std::shared_mutex mutex_{};
    inline static std::atomic<bool> active_ = false;

    SafePointActivator safePointActivator_;
};

template <typename Impl>
ExtraSafePointActionActivator<Impl>::~ExtraSafePointActionActivator() noexcept {
    // First stop new incoming threads from acquiring the mutex.
    active_.store(false, std::memory_order_relaxed);
    // Then synchronize with those, already holding the mutex.
    std::unique_lock lock(mutex_);
}

namespace test_support {

bool safePointsAreActive() noexcept;
void setSafePointAction(void (*action)(mm::ThreadData&)) noexcept;

} // namespace test_support

} // namespace kotlin::mm
