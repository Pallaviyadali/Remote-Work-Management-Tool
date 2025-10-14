public class Main {
    import java.util.Scanner;

// Node class for doubly linked list
class Node {
    int data;
    Node prev;
    Node next;

    Node(int data) {
        this.data = data;
        this.prev = null;
        this.next = null;
    }
}

// Doubly linked list class
class DoublyLinkedList {
    Node head;

    // Insert node at end
    void insertEnd(int data) {
        Node newNode = new Node(data);
        if (head == null) {
            head = newNode;
            return;
        }

        Node temp = head;
        while (temp.next != null) {
            temp = temp.next;
        }

        temp.next = newNode;
        newNode.prev = temp;
    }

    // Reverse the list in-place
    void reverse() {
        if (head == null) return;

        Node current = head;
        Node temp = null;

        // Swap next and prev for all nodes
        while (current != null) {
            temp = current.prev;
            current.prev = current.next;
            current.next = temp;
            current = current.prev;
        }

        // Adjust head to the new front
        if (temp != null) {
            head = temp.prev;
        }
    }

    // Delete all nodes with even values
    void deleteEven() {
        Node current = head;

        while (current != null) {
            Node nextNode = current.next;

            if (current.data % 2 == 0) {
                if (current.prev != null)
                    current.prev.next = current.next;
                else
                    head = current.next;  // deleting head node

                if (current.next != null)
                    current.next.prev = current.prev;
            }

            current = nextNode;
        }
    }

    // Print the list
    void printList() {
        if (head == null) {
            System.out.println("List is empty.");
            return;
        }

        Node temp = head;
        System.out.print("List: ");
        while (temp != null) {
            System.out.print(temp.data + " ");
            temp = temp.next;
        }
        System.out.println();
    }
}

// Main class
public class DoublyLinkedListOperations {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        DoublyLinkedList list = new DoublyLinkedList();

        System.out.print("Enter number of elements: ");
        int n = sc.nextInt();

        System.out.println("Enter elements:");
        for (int i = 0; i < n; i++) {
            list.insertEnd(sc.nextInt());
        }

        System.out.println("\nOriginal list:");
        list.printList();

        list.reverse();
        System.out.println("List after reversing:");
        list.printList();

        list.deleteEven();
        System.out.println("List after deleting even values:");
        list.printList();

        sc.close();
    }
}

    
}
