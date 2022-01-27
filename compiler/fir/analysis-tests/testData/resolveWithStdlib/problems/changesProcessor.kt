// WITH_REFLECT

import kotlin.reflect.KProperty1
import kotlin.properties.ReadWriteProperty

class ProcessorWithParent : Entity {
    var processor by parent(ProcessorWithChildren::processors)
}

class ProcessorWithChildren : Entity {
    var processors by children(ProcessorWithParent::class.java, ProcessorWithParent::processor)
}

inline fun <reified SP : Entity, reified TP : Entity> SP.parent(
    property: KProperty1<TP, MutableCollection<SP>>
): Delegate<SP, TP?> = null!!

fun <SC : Entity, TC : Entity> SC.children(
    clazz: Class<TC>, property: KProperty1<TC, SC?>, name: String = property.name
): Delegate<SC, MutableCollection<TC>> = null!!

interface Delegate<R : Entity, T> : ReadWriteProperty<R, T>

interface Entity
