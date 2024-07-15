//
//  iosAppTests.swift
//  iosAppTests
//
//  Created by Andrey.Yastrebov on 05.02.24.
//  Copyright © 2024 orgName. All rights reserved.
//

import XCTest
@testable import Shared
@testable import Subproject

final class iosAppTests: XCTestCase {

    func testFoo() {
        let result = com.github.jetbrains.swiftexport.foo()
        XCTAssertEqual(result, 321, "foo() should return the expected result")
    }

    func testBar() {
        let result = com.github.jetbrains.swiftexport.bar()
        XCTAssertEqual(result, 123, "bar() should return the expected result")
    }

    func testFoobar() {
        let param: Swift.Int32 = 42
        let result = com.github.jetbrains.swiftexport.foobar(param: param)
        XCTAssertEqual(result, 486, "foobar() should return the expected result for the given parameter")
    }

    func testSubprojectFoo() {
        let result = com.subproject.library.libraryFoo()
        XCTAssertEqual(result, 123456, "libraryFoo() should return the expected result")
    }
}
