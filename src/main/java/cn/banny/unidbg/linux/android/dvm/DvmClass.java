package cn.banny.unidbg.linux.android.dvm;

import cn.banny.unidbg.Emulator;
import cn.banny.unidbg.Module;
import cn.banny.unidbg.Symbol;
import cn.banny.unidbg.linux.LinuxModule;
import cn.banny.unidbg.pointer.UnicornPointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class DvmClass extends DvmObject<String> implements Hashable {

    private static final Log log = LogFactory.getLog(DvmClass.class);

    public final BaseVM vm;
    private final DvmClass[] interfaceClasses;

    DvmClass(BaseVM vm, String className, DvmClass[] interfaceClasses) {
        super("java/lang/Class".equals(className) ? null : vm.resolveClass("java/lang/Class"), className);
        this.vm = vm;
        this.interfaceClasses = interfaceClasses;
    }

    public String getClassName() {
        return value;
    }

    public DvmObject newObject(Object value) {
        DvmObject obj = new DvmObject<>(this, value);
        vm.addObject(obj, false);
        return obj;
    }

    private final Map<Long, DvmMethod> staticMethodMap = new HashMap<>();

    final DvmMethod getStaticMethod(long hash) {
        DvmMethod method = staticMethodMap.get(hash);
        if (method == null) {
            for (DvmClass interfaceClass : interfaceClasses) {
                method = interfaceClass.getStaticMethod(hash);
                if (method != null) {
                    break;
                }
            }
        }
        return method;
    }

    int getStaticMethodID(String methodName, String args) {
        String name = getClassName() + "->" + methodName + args;
        long hash = name.hashCode() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("getStaticMethodID name=" + name + ", hash=0x" + Long.toHexString(hash));
        }
        staticMethodMap.put(hash, new DvmMethod(this, methodName, args));
        return (int) hash;
    }

    private final Map<Long, DvmMethod> methodMap = new HashMap<>();

    final DvmMethod getMethod(long hash) {
        DvmMethod method = methodMap.get(hash);
        if (method == null) {
            for (DvmClass interfaceClass : interfaceClasses) {
                method = interfaceClass.getMethod(hash);
                if (method != null) {
                    break;
                }
            }
        }
        return method;
    }

    int getMethodID(String methodName, String args) {
        String name = getClassName() + "->" + methodName + args;
        long hash = name.hashCode() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("getMethodID name=" + name + ", hash=0x" + Long.toHexString(hash));
        }
        methodMap.put(hash, new DvmMethod(this, methodName, args));
        return (int) hash;
    }

    private final Map<Long, DvmField> fieldMap = new HashMap<>();

    final DvmField getField(long hash) {
        DvmField field = fieldMap.get(hash);
        if (field == null) {
            for (DvmClass interfaceClass : interfaceClasses) {
                field = interfaceClass.getField(hash);
                if (field != null) {
                    break;
                }
            }
        }
        return field;
    }

    int getFieldID(String fieldName, String fieldType) {
        String name = getClassName() + "->" + fieldName + ":" + fieldType;
        long hash = name.hashCode() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("getFieldID name=" + name + ", hash=0x" + Long.toHexString(hash));
        }
        fieldMap.put(hash, new DvmField(this, fieldName, fieldType));
        return (int) hash;
    }

    private final Map<Long, DvmField> staticFieldMap = new HashMap<>();

    final DvmField getStaticField(long hash) {
        DvmField field = staticFieldMap.get(hash);
        if (field == null) {
            for (DvmClass interfaceClass : interfaceClasses) {
                field = interfaceClass.getStaticField(hash);
                if (field != null) {
                    break;
                }
            }
        }
        return field;
    }

    int getStaticFieldID(String fieldName, String fieldType) {
        String name = getClassName() + "->" + fieldName + ":" + fieldType;
        long hash = name.hashCode() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("getStaticFieldID name=" + name + ", hash=0x" + Long.toHexString(hash));
        }
        staticFieldMap.put(hash, new DvmField(this, fieldName, fieldType));
        return (int) hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DvmClass dvmClass = (DvmClass) o;
        return Objects.equals(getClassName(), dvmClass.getClassName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClassName());
    }

    @Override
    public String toString() {
        return getClassName();
    }

    final Map<String, UnicornPointer> nativesMap = new HashMap<>();

    UnicornPointer findNativeFunction(Emulator emulator, String method) {
        UnicornPointer fnPtr = nativesMap.get(method);
        int index = method.indexOf('(');
        if (fnPtr == null && index != -1) {
            String symbolName = "Java_" + getClassName().replace('/', '_') + "_" + method.substring(0, index);
            for (Module module : emulator.getMemory().getLoadedModules()) {
                Symbol symbol = module.findSymbolByName(symbolName, false);
                if (symbol != null) {
                    fnPtr = (UnicornPointer) symbol.createPointer(emulator);
                    break;
                }
            }
        }
        if (fnPtr == null) {
            throw new IllegalArgumentException("find method failed: " + method);
        }
        return fnPtr;
    }

    public Number callStaticJniMethod(Emulator emulator, String method, Object...args) {
        UnicornPointer fnPtr = findNativeFunction(emulator, method);
        List<Object> list = new ArrayList<>(10);
        list.add(vm.getJNIEnv());
        list.add(this.hashCode());
        if (args != null) {
            for (Object arg : args) {
                list.add(arg);

                if(arg instanceof DvmObject) {
                    vm.addLocalObject((DvmObject) arg);
                }
            }
        }
        return LinuxModule.emulateFunction(emulator, fnPtr.peer, list.toArray())[0];
    }

}
