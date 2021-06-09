package client;

import commands.Command;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public TextArea textArea;
    @FXML
    public TextField textField;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public HBox authPanel;
    @FXML
    public HBox msgPanel;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final int PORT = 8889;
    private final String IP_ADDRESS = "localhost";

    private boolean authenticated;
    private String nickname;
    private String login;
    private Stage stage;
    private Stage regStage;
    private RegController regController;


    //    Метод отображение  основного окна и регистрации
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);

        if (!authenticated) {
            nickname = "";

        }
        textArea.clear();
        setTitle(nickname);
    }

    //  инициализируем окно клиента
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            stage = (Stage) textArea.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                System.out.println("bye");
                if (socket != null && !socket.isClosed()) {
                    try {
                        out.writeUTF(Command.END);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        });
        setAuthenticated(false);
    }

    //  Подключение к хосту
    private void connect() {
        try {
            socket = new Socket(IP_ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    // цикл аутентификации
                    while (true) {

                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                throw new RuntimeException("Сервак нас отключает");
                            }
                            if (str.startsWith(Command.AUTH_OK)) {
                                String[] token = str.split("\\s");
                                nickname = token[1];
                                setAuthenticated(true);

                                break;
                            }
                            if (str.equals(Command.REG_OK)) {
                                regController.setResultTryToReg(Command.REG_OK);
                            }

                            if (str.equals(Command.REG_NO)) {
                                regController.setResultTryToReg(Command.REG_NO);
                            }

                        } else {

                            textArea.appendText(str + "\n");
                        }
                    }

                } catch (RuntimeException e) {
                    System.out.println(e.getMessage());
                    setAuthenticated(false);
                    try {
                        socket.close();
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    setAuthenticated(false);
                    try {
                        socket.close();
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                }

            }).start();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Кнопка help
     *
     * @param actionEvent
     */
    public void command(ActionEvent actionEvent) {
        textArea.clear();
        textArea.appendText("Hello user!" + "\n" +
                "You can use the following commands:" + "\n" +
                "/end" + "\n" +
                "/upload" + "\n" +
                "/download , " + "\n" +
                "/mkdir" + "\n" +
                "/delete" + "\n" +
                "/create" + "\n" +
                "/chnick" + "\n" +
                "/ls" + "\n" +
                "/cd" + "\n" +
                "/sort" + "\n" +
                "/rename" + "\n" +
                "/search" + "\n" +
                ">: \\server\\serverfile\\ " + "\n");


        textField.requestFocus();

    }

    /**
     * Кнопка Аутентификации
     *
     * @param actionEvent
     */
    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        login = loginField.getText().trim();

        try {
            out.writeUTF(String.format("%s %s %s", Command.AUTH, loginField.getText().trim(), passwordField.getText().trim()));

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            passwordField.clear();
        }
    }

    /**
     * Кнопка Command
     *
     * @param actionEvent
     */
    public void sendCommand(ActionEvent actionEvent) {
        try {
            String[] cmd = textField.getText().split(" ");
//            if ("/end".equals(cmd[0])) {
//                out.writeUTF(Command.END);
//
//            }
            if ("/chnick".equals(cmd[0])) {
                chnick(cmd[1]);
            }

            if ("/upload".equals(cmd[0])) {
                sendFile(cmd[1]);
            }

            if ("/download".equals(cmd[0])) {
                getFile(cmd[1]);
            }

            if ("/mkdir".equals(cmd[0])) {
                mkDirs(cmd[1]);
            }
            if ("/search".equals(cmd[0])) {
                search();
            }
            if ("/delete".equals(cmd[0])) {
                deleteFail(cmd[1]);
            }
            if ("/create".equals(cmd[0])) {
                createFile(cmd[1]);
            }
            if ("/cd".equals(cmd[0])) {
                pathDir(cmd[1]);
            }
            if ("/ls".equals(cmd[0])) {
                listFile();
            }
            if ("/sort".equals(cmd[0])) {
                sortFile(cmd[1]);
            }
            if ("/rename".equals(cmd[0])) {
                rename(cmd[1], cmd[2]);
            }
            if ("/end".equals(cmd[0])) {
                try {
                    out.writeUTF(Command.END);
                    socket.close();
                    setAuthenticated(false);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
            textField.clear();
            textField.requestFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
//        метод перемеинования файла
    private void rename(String oldname, String newname) {
        try {
            out.writeUTF("/rename");
            out.writeUTF(oldname);
            out.writeUTF(newname);

            String text = null;
            try {
                text = in.readUTF();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            textArea.appendText(text + "\n");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
//      метод сориторвки
    private void sortFile(String cmd) {
        try {
            out.writeUTF("/sort");
            out.writeUTF(cmd);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        String text = null;
        try {
            text = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        textArea.appendText(text + "\n");

    }
//      метод вывода всех файлов
    private void listFile() {
        try {
            out.writeUTF("/ls");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        String text = null;
        try {
            text = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        textArea.appendText(text + "\n");
    }

    //          метод смены ника
    private void chnick(String newNick) {
        try {
            out.writeUTF("/chnick");
            out.writeUTF(newNick);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        String text = null;
        try {
            text = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        textArea.appendText(text + "\n");
        nickname = newNick;
        setTitle(nickname);
        if ("WRONG".equals(text)) {
            textArea.appendText("Не удалось изменить ник. Ник " + newNick + " уже существует");
        }

    }

    //       метод перехода по директориям
    private void pathDir(String path) {
        try {
            out.writeUTF("/cd");
            out.writeUTF(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String text = null;
        try {
            text = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        textArea.appendText(text + "\n");
    }

    //     метод показать размер файлов
    private void search() {
        try {
            out.writeUTF("/search");
            String p = in.readUTF();
            while (!p.equals("OK") && !p.equals("WRONG")) {
                textArea.appendText(p + "\n");
                p = in.readUTF();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }


    //           получить файл
    private void getFile(String filename) {
        try {
            File file = new File(".\\server\\serverfile\\" + filename);
            if (!file.exists()) {
                throw new FileNotFoundException();
            }

            long fileLength = file.length();
            FileInputStream fis = new FileInputStream(file);

            out.writeUTF("/download");
            out.writeUTF(filename);
            out.writeUTF("" + fileLength);

            int read = 0;
            byte[] buffer = new byte[8 * 1024];
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            out.flush();

            String status = in.readUTF();
            System.out.println("Sending status: " + status);
            textArea.appendText("Sending status: " + status + "\n");
        } catch (FileNotFoundException e) {
            System.err.println("File not found - /serverfile/" + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    //    отправка файла
    private void sendFile(String filename) {
        try {
            File file = new File(".\\client\\clientfile\\" + filename);
            if (!file.exists()) {
                throw new FileNotFoundException();
            }

            long fileLength = file.length();
            FileInputStream fis = new FileInputStream(file);

            out.writeUTF("/upload");
            out.writeUTF(filename);
            out.writeUTF("" + fileLength);

            int read = 0;
            byte[] buffer = new byte[8 * 1024];
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            out.flush();

            String status = in.readUTF();
            System.out.println("Sending status: " + status);
            textArea.appendText("Sending status: " + status + "\n");
        } catch (FileNotFoundException e) {
            System.err.println("File not found - /clientfile/" + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //    в окне виден ник пользователя
    private void setTitle(String nickname) {
        Platform.runLater(() -> {
            if (nickname.equals("")) {
                stage.setTitle("Cloud-Storage");
            } else {
                stage.setTitle(String.format("Cloud-Storage - [ %s ]", nickname));
            }
        });
    }

    public void showRegWindow(ActionEvent actionEvent) {
        if (regStage == null) {
            initRegWindow();
        }
        regStage.show();
    }

    private void initRegWindow() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml"));
            Parent root = fxmlLoader.load();

            regController = fxmlLoader.getController();
            regController.setController(this);

            regStage = new Stage();
            regStage.setTitle("Cloud-Storage");
            regStage.setScene(new Scene(root, 450, 350));
            regStage.initStyle(StageStyle.UTILITY);
            regStage.initModality(Modality.APPLICATION_MODAL);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //          создание директрории
    private void mkDirs(String path) {
        try {
            out.writeUTF("/mkdir");
            out.writeUTF(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String text = null;
        try {
            text = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        textArea.appendText(text + "\n");
    }


    //          метод создание файла
    private void createFile(String path) {
        try {
            out.writeUTF("/create");
            out.writeUTF(path);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        String text = null;
        try {
            text = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        textArea.appendText(text + "\n");
    }

    //        Метод удаления файла или каталога
    private void deleteFail(String path) {
        try {
            out.writeUTF("/delete");
            out.writeUTF(path);
        } catch (IOException exception) {
            exception.printStackTrace();

        }
        String text = null;
        try {
            text = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        textArea.appendText(text + "\n");
    }

    public void registration(String login, String password, String nickname) {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF(String.format("%s %s %s %s", Command.REG, login, password, nickname));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
