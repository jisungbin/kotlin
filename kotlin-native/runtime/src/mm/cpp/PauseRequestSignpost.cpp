/*
 * Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "PauseRequestSignpost.hpp"

#include "CompilerConstants.hpp"

#if KONAN_SUPPORTS_SIGNPOSTS

#include <os/log.h>
#include <os/signpost.h>

using namespace kotlin;

namespace {

os_log_t logObject = os_log_create("org.kotlinlang.native.runtime", "safepoint");

}

#define PAUSE_REQUEST_SIGNPOST_NAME "Pause request" // signpost API requires strings be literals

mm::PauseRequestSignpost::PauseRequestSignpost(const char* reason) noexcept : id_(os_signpost_id_generate(logObject)) {
    os_signpost_interval_begin(logObject, id_, PAUSE_REQUEST_SIGNPOST_NAME, "reason: %s", reason);
}

mm::PauseRequestSignpost::~PauseRequestSignpost() {
    if (id_ == 0) return;
    os_signpost_interval_end(logObject, id_, PAUSE_REQUEST_SIGNPOST_NAME);
}

#undef PAUSE_REQUEST_SIGNPOST_NAME

#else

mm::PauseRequestSignpost::PauseRequestSignpost(const char* reason) noexcept {}
mm::PauseRequestSignpost::~PauseRequestSignpost() = default;

#endif

// static
std::optional<mm::PauseRequestSignpost> mm::PauseRequestSignpost::create(const char* reason) noexcept {
    if (compiler::enableSafepointSignposts()) {
        return mm::PauseRequestSignpost(reason);
    }
    return std::nullopt;
}
