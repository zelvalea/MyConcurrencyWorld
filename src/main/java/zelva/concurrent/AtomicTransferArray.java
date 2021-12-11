package zelva.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Demo Array Atomic Resize
 * Идея заключаетя в атомарной маркировке старого массива
 * заполняя его специальной TransferNode, в которой есть поле-указатель
 * на новый массив - оно мутабельное
 * новый массив атомарно заполняется элементами из старого массива
 * один за другим
 * для удобной модификации используется Node с волатильным значением
 *
 * @since 9
 * @author ZelvaLea
 * @param <E>
 */
public class AtomicTransferArray<E> {
    /*
     *  [T] [1] [2] [3] [4] [5] [6] [7] [8] [9]  |  OLD ARRAY
     *   ^   ^   ^   ^   ^   ^   ^   ^   ^   ^
     *   v   v   v   v   v   v   v   v   v   v
     *  [0]>[N]>[N]>[N]>[N]>[N]>[N]>[N]>[N]>[N]  |  NEW ARRAY
     *
     * 
     * 
     * transfer:
     * 
     *   (T)    (T)    (F)
     *      \      \
     *      (T)    (F)
     *      /
     *   (F)
     */
    private static final int MIN_CAPACITY = 1;
    private static final int INITIAL_CAPACITY = 16;
    volatile Node<E>[] array;

    public AtomicTransferArray(int size) {
        this.array = prepareArray(size);
    }
    public AtomicTransferArray() {
        this(INITIAL_CAPACITY);
    }

    public boolean compareAndSet(int i, E c, E v) {
        for (Node<E>[] arr = array;;) {
            Node<E> f; E e;
            if (((f = arrayAt(arr, i))) == null) {
                return c == null ||
                        casArrayAt(arr, i, null, new Node<>(v));
            } else if ((e = f.element) != c) {
                return false;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.next;
            } else {
                return VAL.compareAndSet(f, e, v);
            }
        }
    }

    private Node<E>[] setIfAbsent(int i, Node<E>[] arr, Node<E> c) {
        for (Node<E> f;;) {
            if ((f = arrayAt(arr, i)) == null
                    && weakCasArrayAt(arr, i, null, c)) {
                return arr;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.next;
            } else {
                return arr;
            }
        }
    }

    public E set(int i, E element) {
        Node<E>[] arr = array;
        boolean remove = element == null;
        for (Node<E> f;;) {
            if ((f = arrayAt(arr, i)) == null) {
                if (remove || weakCasArrayAt(arr, i, null,
                        new Node<>(element))) {
                    return null;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = t.helpTransfer();
            } else {
                E e = f.element;
                if (remove) {
                    if (weakCasArrayAt(arr, i, f, null)) {
                        return e;
                    }
                } else {
                    return element != e
                            ? f.element = element : e;
                }
            }
        }
    }

    public E get(int i) {
        for (Node<E>[] arr = array;;) {
            Node<E> f;
            if ((f = arrayAt(arr, i)) == null) {
                return null;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.next;
            } else {
                return f.element;
            }
        }
    }
    public void resize(int size) {
        Node<E>[] next = prepareArray(size), prev;
        final TransferNode<E> tfn = new TransferNode<>(this, next,
                prev = array);
        for (int i = 0,
             len = transferBound(prev.length, size);
             i < len && tfn.prev != null;
             ++i) {
            for (Node<E> f; tfn.prev != null; ) {
                if (tfn.prev == null) {
                    return;
                }
                if ((f = arrayAt(prev, i))
                        instanceof TransferNode<E> t) {
                    prev = t.helpTransfer();
                    len = transferBound(prev.length, size);
                } else {
                    if (f != null)
                        setAt(next, i, f);
                    if (casArrayAt(prev, i, f, tfn)) {
                        break;
                    }
                }
            }
        }
        tfn.prev = null; // help gc
        array = next;
    }
    @SuppressWarnings("unchecked")
    private static <E> Node<E>[] prepareArray(int size) {
        return new Node[Math.max(MIN_CAPACITY, size)];
    }
    private static int transferBound(int x, int y) {
        return Math.min(x, y);
    }

    public int size() {
        return array.length;
    }

    // test
    public void clear() {
        Node<E>[] arr = array;
        for (int i = 0; i < arr.length;) {
            Node<E> f = arrayAt(arr, i);
            if (f == null) {
                ++i;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.next;
            } else if (weakCasArrayAt(arr, i, f, null)) {
                ++i;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int i = 0;
        for (Node<E>[] arr = array;;) {
            Node<E> f = arrayAt(arr, i);
            if (f instanceof TransferNode<E> t) {
                arr = t.next;
            } else {
                sb.append(f);
                if (++i >= arr.length)
                    return sb.append(']').toString();
                sb.append(", ");
            }
        }
    }

    private static class Node<E> {
        volatile E element;

        Node(E element) {
            this.element = element;
        }
        @Override
        public String toString() {
            E e = element;
            return e == null ? "EmptyNode" : e.toString();
        }
    }

    private static class TransferNode<E> extends Node<E> {
        final AtomicTransferArray<E> self;
        final Node<E>[] next;
        volatile Node<E>[] prev; // todo: non volatile

        TransferNode(AtomicTransferArray<E> self, Node<E>[] transfer, Node<E>[] prev) {
            super(null);
            this.self = self;
            this.next = transfer;
            this.prev = prev;
        }

        Node<E>[] helpTransfer() {
            Node<E>[] nodes = prev;
            if (nodes != null) {
                outer: for (int i = nodes.length - 1; i >= 0; --i) {
                    if (prev == null) return next;
                    for (Node<E> f; prev != null; ) {
                        if ((f = arrayAt(nodes, i)) == this) {
                            continue outer;
                        } else if (f instanceof TransferNode<E> t) {
                            t.helpTransfer();
                        } else {
                            if (f != null)
                                setAt(next, i, f);
                            if (casArrayAt(nodes, i, f, this)) {
                                continue outer;
                            }
                        }
                    }
                }
                if (PREV.compareAndSet(this, nodes, null))
                    self.array = next;
            }
            return next;
        }
        @Override
        public String toString() {
            return "TransferNode "+ element;
        }
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> arrayAt(Node<E>[] arr, int i) {
        return (Node<E>) AA.getVolatile(arr, i); // todo: acquire
    }
    static <E> void setAt(Node<E>[] arr, int i, Node<E> v) {
        AA.setVolatile(arr, i, v); // todo: release
    }
    static <E> boolean weakCasArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.weakCompareAndSet(arr, i, c, v);
    }
    static <E> boolean casArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.compareAndSet(arr, i, c, v);
    }
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle VAL, PREV;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(Node.class, "element", Object.class);
            PREV = l.findVarHandle(TransferNode.class, "prev", Node[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
