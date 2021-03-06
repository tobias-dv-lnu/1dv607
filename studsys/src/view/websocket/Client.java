package view.websocket;

import model.Student;
import view.AdminOption;
import view.Helper;
import view.StudentOption;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;

public class Client implements view.IUI {
    Socket m_socket;
    DataInputStream m_in;   // receive
    DataOutputStream m_out; // send

    String m_name;
    String m_email;

    Queue<Frame> m_frameQueue = new LinkedList<>();

    public Client(Socket a_clientSocket, InputStream a_in, OutputStream a_out) {
        m_socket = a_clientSocket;
        m_in = new DataInputStream(a_in);
        m_out = new DataOutputStream(a_out);
    }

    private String buildStudentDataString(Student a_student) {
        final Helper h = new Helper();

        return a_student.getName().str() + ":" + h.toString(a_student.getEmail());
    }

    private String[] readMessage() throws IOException {

        while(true) {
            if (m_in.available() > 0) {
                try {
                    Frame replyF = new Frame(m_in);
                    if (replyF.isText()) {
                        String reply = new String(replyF.getData(), StandardCharsets.UTF_8);
                        String parts[] = reply.split(":");
                        return parts;
                    } else if (replyF.isPing()) {
                        // reply with a pong asap
                        replyF.createPong().send(m_out);
                    } else if (replyF.isPong()) {
                        // we are currently not sending any pings
                        // however IE seems to send unsolicited pongs
                    }
                } catch (Frame.ConnectionClosedException e) {
                    // TODO: send the correct close connection reply
                    // then we close
                    throw e;
                }
            } else {
                if (!m_frameQueue.isEmpty()) {
                    Frame f = m_frameQueue.poll();
                    f.send(m_out);
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(String a_msg) throws  IOException {
        Frame f = new Frame(a_msg);
        f.send(m_out);
    }

    @Override
    public boolean showStudentForm(Student a_selectedStudent) throws IOException {
        m_name = "";
        m_email = "";
        String msg = new String("showStudentForm");
        if (a_selectedStudent != null) {
            msg = msg + ":" + buildStudentDataString(a_selectedStudent);
        }
        sendMessage(msg);
        String parts[] = readMessage();
        if (parts[0].equalsIgnoreCase("false")) {
            return false;
        } else if (parts[0].equalsIgnoreCase("true")) {
            if (parts.length == 3) {
                m_name = parts[1];
                m_email = parts[2];
                return true;
            } else {
                throw new IOException("Expected 3 parts in reply from client, got: " + parts.length);
            }
        } else {
            throw new IOException("Unknow reply from client: " + parts[0]);
        }
    }

    @Override
    public String getName() {
        return m_name;
    }

    @Override
    public String getEmail() {
        return m_email;
    }

    @Override
    public void showAddedStudentConfirmation(Student a_s) throws IOException {
        sendMessage("showAddedStudentConfirmation:" +buildStudentDataString(a_s));
    }

    @Override
    public Student showStudentList(Iterable<Student> a_students) throws IOException {
        Helper h = new Helper();
        String msg = "showStudentList";
        for (Student s : a_students) {
            msg = msg + ":" + buildStudentDataString(s);
        }

        sendMessage(msg);

        String [] reply = readMessage();
        if (!reply[0].equalsIgnoreCase("none")) {
            try {
                int ixInt = Integer.parseInt(reply[0]);
                int ix = 0;
                for (Student s : a_students) {
                    if (ix == ixInt) {
                        return s;
                    }
                    ix++;
                }

                throw new IOException("Invalid index: " + ixInt);
            } catch (NumberFormatException e) {
                throw new IOException("Index not an integer: " + reply[0]);
            }
        }

        return null;
    }

    @Override
    public AdminOption getAdminOptions() throws IOException {
        sendMessage("getAdminOptions");
        String reply[] = readMessage();

        for (AdminOption o : AdminOption.values()) {
            if (o.name().equalsIgnoreCase(reply[0])) {
                return o;
            }
        }

        throw new IOException("Unknown reply: " + reply[0]);
    }

    @Override
    public StudentOption getStudentOptions(Student a_selectedStudent) throws IOException {
        sendMessage("getStudentOption:" + buildStudentDataString(a_selectedStudent));
        String reply[] = readMessage();

        for (StudentOption o : StudentOption.values()) {
            if (o.name().equalsIgnoreCase(reply[0])) {
                return o;
            }
        }

        throw new IOException("Unknown reply: " + reply[0]);
    }

    @Override
    public void onAddNewStudent(Iterable<Student> a_allStudents, Student a_newStudent) {
        // basically we should send this to the remote client as an extra message
        // but this needs to be done in a message pump way, maybe in readMessage as this is where we
        // idle... lets try it...
        final String[] msg = {"onAddNewStudent:" + buildStudentDataString(a_newStudent)};
        a_allStudents.forEach(s -> msg[0] += ":" + buildStudentDataString(s));
        m_frameQueue.add(new Frame(msg[0]));
    }

    @Override
    public void onDeleteStudent(Iterable<Student> a_allStudents, Student a_deletedStudent) {
        final String[] msg = {"onDeletedStudent:" + buildStudentDataString(a_deletedStudent)};
        a_allStudents.forEach(s -> msg[0] += ":" + buildStudentDataString(s));
        m_frameQueue.add(new Frame(msg[0]));
    }

    @Override
    public void onChangeActiveStudent(Student a_newStudent) {
        final String msg = "onChangedStudent:" + buildStudentDataString(a_newStudent);
        m_frameQueue.add(new Frame(msg));
    }

    public void waitForReady() throws IOException {
        String msg = readMessage()[0];
        if (!msg.equalsIgnoreCase("ready")) {
            throw new IOException("Client not ready: " + msg);
        }
    }

    public void forceClose() {
        try {
            m_in.close();
            m_out.close();
            m_socket.close();
        } catch (IOException e) {

        }
    }
}
