/**
 * Java/Kotlin bridge type definitions.
 *
 * Rhino-like usage:
 *   const Cls = Java.type("java.lang.StringBuilder");
 *   const obj = Cls.newInstance();
 *   obj.append("hello");
 *   const out = obj.toString();
 */

/**
 * Opaque handle payload used when values cross the native bridge boundary.
 */
export interface JavaBridgeHandle {
    __javaHandle: string;
    __javaClass: string;
}

/**
 * Marker object produced by `Java.implement(...)`.
 * Pass this object to Java methods/constructors expecting interface arguments.
 */
export interface JavaBridgeJsInterfaceMarker {
    __javaJsInterface: true;
    __javaJsObjectId: string;
    __javaInterfaces: string[];
}

/**
 * JS implementation target for Java interface callbacks.
 * - function: single callable target (SAM-style usage)
 * - object: method-name based implementation
 */
export type JavaBridgeJsInterfaceImpl =
    ((...args: any[]) => any) |
    Record<string, any>;

/**
 * Dynamic proxy object for a Java/Kotlin instance.
 * - Unknown property reads first try instance field/property get, then fallback to method callable.
 * - Unknown property writes are treated as instance field/property set.
 */
export interface JavaBridgeInstance extends JavaBridgeHandle {
    readonly className: string;
    readonly handle: string;

    call<T = any>(methodName: string, ...args: any[]): T;
    get<T = any>(): T;
    get<T = any>(fieldName: string): T;
    set<T = any>(value: any): T;
    set<T = any>(fieldName: string, value: any): T;
    release(): boolean;

    toJSON(): JavaBridgeHandle;
    toString(): string;

    [member: string]: any;
}

/**
 * Dynamic proxy object for a Java/Kotlin class.
 * - Unknown property reads first try static field/property get, then fallback to static method callable.
 * - Unknown property writes are treated as static field/property set.
 */
export interface JavaBridgeClass {
    (...args: any[]): JavaBridgeInstance;
    new (...args: any[]): JavaBridgeInstance;

    readonly className: string;

    exists(): boolean;
    newInstance<T extends JavaBridgeInstance = JavaBridgeInstance>(...args: any[]): T;
    callStatic<T = any>(methodName: string, ...args: any[]): T;
    getStatic<T = any>(fieldName: string): T;
    setStatic<T = any>(fieldName: string, value: any): T;
    toString(): string;

    [member: string]: any;
}

/**
 * Dynamic package namespace proxy:
 * - e.g. `Java.java.lang.System`
 */
export interface JavaBridgePackage {
    (...args: any[]): JavaBridgeInstance;
    new (...args: any[]): JavaBridgeInstance;

    readonly path: string;
    toString(): string;

    [member: string]: any;
}

/**
 * Top-level Java/Kotlin bridge API injected by runtime.
 */
export interface JavaBridgeApi {
    type(className: string): JavaBridgeClass;
    use(className: string): JavaBridgeClass;
    importClass(className: string): JavaBridgeClass;
    package(packageName: string): JavaBridgePackage;
    implement(interfaceName: string, impl: JavaBridgeJsInterfaceImpl): JavaBridgeJsInterfaceMarker;
    implement(interfaceNames: string[], impl: JavaBridgeJsInterfaceImpl): JavaBridgeJsInterfaceMarker;
    implement(impl: JavaBridgeJsInterfaceImpl): JavaBridgeJsInterfaceMarker;
    proxy(interfaceName: string, impl: JavaBridgeJsInterfaceImpl): JavaBridgeJsInterfaceMarker;
    proxy(interfaceNames: string[], impl: JavaBridgeJsInterfaceImpl): JavaBridgeJsInterfaceMarker;
    proxy(impl: JavaBridgeJsInterfaceImpl): JavaBridgeJsInterfaceMarker;
    releaseJs(objectOrId: JavaBridgeJsInterfaceMarker | string): boolean;
    classExists(className: string): boolean;
    callStatic<T = any>(className: string, methodName: string, ...args: any[]): T;
    newInstance<T extends JavaBridgeInstance = JavaBridgeInstance>(className: string, ...args: any[]): T;
    release(instanceOrHandle: JavaBridgeInstance | JavaBridgeHandle | string): boolean;
    releaseAll(): number;
    getApplicationContext<T extends JavaBridgeInstance = JavaBridgeInstance>(): T;
    getContext<T extends JavaBridgeInstance = JavaBridgeInstance>(): T;
    getCurrentActivity<T extends JavaBridgeInstance = JavaBridgeInstance>(): T;
    getActivity<T extends JavaBridgeInstance = JavaBridgeInstance>(): T;

    [member: string]: any;
}
