package server;


import commands.Command;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


public class ClientHandler {
    private static final String ROOT_NOTIFICATION = "You are already in the root directory\n\n";
    private static final String ROOT_PATH = ".\\server\\serverfile\\";
    private static final String DIRECTORY_DOESNT_EXIST = "Directory %s doesn't exist\n\n";
    private Server server;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String nickname;
    private String login;
    private String pathServer = ".\\server\\serverfile\\";
    private Path currentPath = Path.of("server", "serverfile");

    public ClientHandler(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        new Thread(() -> {
            try {
                // установка сокет тайм аут
                socket.setSoTimeout(120000);

                // цикл аутентификации
                while (true) {
                    String str = in.readUTF();

                    // если команда отключиться
                    if (str.equals(Command.END)) {
                        out.writeUTF(Command.END);
                        throw new RuntimeException("Клиент захотел отключиться");
                    }

                    //если команда аутентификация
                    if (str.startsWith(Command.AUTH)) {
                        String[] token = str.split("\\s", 3);
                        if (token.length < 3) {
                            continue;
                        }
                        String newNick = server.getAuthService()
                                .getNicknameByLoginAndPassword(token[1], token[2]);
                        login = token[1];
                        if (newNick != null) {
                            if (!server.isLoginAuthenticated(login)) {
                                nickname = newNick;
                                sendMsg(Command.AUTH_OK + " " + nickname);
                                server.subscribe(this);
//
                                socket.setSoTimeout(0);
                                break;
                            } else {
                                sendMsg("Данная учетная запись уже используется");
                            }
                        } else {
                            sendMsg("Неверный логин / пароль");
                        }
                    }

                    //если команда регистрация
                    if (str.startsWith(Command.REG)) {
                        String[] token = str.split("\\s", 4);
                        if (token.length < 4) {
                            continue;
                        }
                        boolean regSuccess = server.getAuthService()
                                .registration(token[1], token[2], token[3]);
                        if (regSuccess) {
                            sendMsg(Command.REG_OK);
                        } else {
                            sendMsg(Command.REG_NO);
                        }
                    }
                }


                // цикл работы
                while (true) {
                    socket.setSoTimeout(0);

                    String str = in.readUTF();

                    if (str.equals("/end")) {
                        out.writeUTF("/end");
                        System.out.println("Client disconnected");
                        break;
                    }
                    if ("/upload".equals(str)) {
                        upload();
                    }
                    if ("/download".equals(str)) {
                        dowmload();
                    }
                    if ("/mkdir".equals(str)) {
                        mkDirs();
                    }
                    if ("/search".equals(str)) {
                        Search();
                    }
                    if ("/delete".equals(str)) {
                        deleteFile();
                    }
                    if ("/create".equals(str)) {
                        createFile();
                    }
                    if ("/cd".equals(str)) {
                        pathDir();
                    }
                    if ("/chnick".equals(str)) {
                        chnick();
                    }
                    if ("/ls".equals(str)) {
                        getFileList();
                    }
                    if ("/sort".equals(str)) {
                        sortFile();
                    }
                    if ("/rename".equals(str)) {
                        rename();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                server.unsubscribe(this);
                System.out.println("Client disconnected: " + nickname);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }).start();
    }

    //    метод перемеиновать файл
    private void rename() {
        try {
            String oldname = in.readUTF();
            String newname = in.readUTF();

            File oldfile = new File(Path.of(currentPath.toString(), oldname).toString());
            File newfile = new File(Path.of(currentPath.toString(), newname).toString());
            boolean izrename = oldfile.renameTo(newfile);

            out.writeUTF("File rename");

        } catch (IOException exception) {
            exception.printStackTrace();
        }

    }

    // метод соритровка ( data | name | size )
    private void sortFile() {

        try {
            String cmd = in.readUTF();
            File[] list = new File(currentPath.toString()).listFiles();
            Comparator<File> comparator = null;
            if (cmd.equals("date")) {

                comparator = new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        LocalDate o1Date = Instant.ofEpochMilli(o1.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate();
                        LocalDate o2Date = Instant.ofEpochMilli(o2.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate();
                        return o1Date.compareTo(o2Date);
                    }
                };
            } else if (cmd.equals("name")) {
                comparator = new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {

                        return o1.getName().compareTo(o2.getName());
                    }
                };

            } else if (cmd.equals("size")) {
                comparator = new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        try {
                            return Long.compare(Files.size(o1.toPath()), Files.size(o2.toPath()));

                        } catch (IOException exception) {
                            exception.printStackTrace();
                        }
                        return 0;
                    }
                };
            }

            Arrays.sort(list, comparator);
            StringBuilder stringBuilder = new StringBuilder();
            for (File f : list) {
                stringBuilder.append(f.getName());
                stringBuilder.append(" ");
                stringBuilder.append(Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate());
                stringBuilder.append("\n");
            }
            out.writeUTF(stringBuilder.toString());

        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    //  метод смены ника
    private void chnick() {
        String newNick = null;
        try {
            newNick = in.readUTF();
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        if (newNick.contains(" ")) {
            sendMsg("Ник не может содержать пробелов");

        }
        if (server.getAuthService().changeNick(this.nickname, newNick)) {
            sendMsg("Ваш ник изменен на " + newNick);
            this.nickname = newNick;
        } else {
            sendMsg("WRONG");

        }
    }

    // метод переход между каталогами
    private void pathDir() throws IOException {

        String needPathString = (in.readUTF() + File.separator);


        Path tempPath = Path.of(currentPath.toString(), needPathString);
        if ("..\\".equals(needPathString)) {
            tempPath = currentPath.getParent();
            if (tempPath == null ) {
                out.writeUTF(ROOT_NOTIFICATION);
            } else {
                currentPath = tempPath;
                out.writeUTF(">: " + currentPath);
            }
        } else if ("~\\".equals(needPathString)) {
            currentPath = Path.of(ROOT_PATH);
            out.writeUTF(">: " + ROOT_PATH);
        } else {
            if (tempPath.toFile().exists()) {
                currentPath = tempPath;
                out.writeUTF(String.valueOf(">: " + tempPath));
            } else
                out.writeUTF(String.format(DIRECTORY_DOESNT_EXIST, tempPath));
        }

    }

    //      отдать файл
    private void upload() {
        try {
            File file = new File(pathServer + in.readUTF()); // read file name
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fil = new FileOutputStream(file);
            long size = Long.parseLong(in.readUTF());
            byte[] buffer = new byte[8 * 1024];
            for (int i = 0; i < (size + (8 * 1024 - 1)) / (8 * 1024); i++) {
                int read = in.read(buffer);
                fil.write(buffer, 0, read);
            }
            fil.close();
            out.writeUTF("OK");
        } catch (Exception e) {
            try {
                out.writeUTF("WRONG");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    //           получить файл
    private void dowmload() {
        try {
            File file = new File(".\\client\\clientfile\\" + in.readUTF()); // read file name
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fil = new FileOutputStream(file);
            long size = Long.parseLong(in.readUTF());
            byte[] buffer = new byte[8 * 1024];
            for (int i = 0; i < (size + (8 * 1024 - 1)) / (8 * 1024); i++) {
                int read = in.read(buffer);
                fil.write(buffer, 0, read);
            }
            fil.close();
            out.writeUTF("OK");
        } catch (Exception e) {
            try {
                out.writeUTF("WRONG");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    //    метод создания файла
    private void createFile() {
        try {
            String path = (in.readUTF() + File.separator);
            path = currentPath + File.separator + path;
            if (Files.exists(Path.of(path))) {
                throw new FileNotFoundException();
            }
            Files.createFile(Path.of(path));


            out.writeUTF("File create");

        } catch (IOException e) {
            try {
                out.writeUTF("WRONG");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    //     метод удаления файла или каталога
    private void deleteFile() {
        try {
            String path = in.readUTF();
            Path path1 = Path.of(pathServer + path);
            Files.delete(path1);

            out.writeUTF("File " + path + " deletes");

        } catch (IOException exception) {
            try {
                out.writeUTF("WRONG");
            } catch (IOException e) {
                e.printStackTrace();
            }
            exception.printStackTrace();
        }
    }


    //          Метод показать размер всех фалов директории
    private void Search() {

        Path path = Path.of(String.valueOf(pathServer));

        try (DirectoryStream<Path> files = Files.newDirectoryStream(path)) {
            for (Path p : files) {
                File f = new File(p.toString());
                String size = "";
                if (f.isFile()) {
                    size = " " + f.length() + " byte";

                }
                out.writeUTF(p.toString().replace(".\\server\\serverfile\\", "") + size);

            }
            out.writeUTF("OK");

        } catch (IOException exception) {
            exception.printStackTrace();
            try {
                out.writeUTF("WRONG");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //        метод создания директории
    private void mkDirs() {
        try {
            String path = in.readUTF();
            Path path1 = Path.of(pathServer + path);
            if (Files.exists(Path.of(String.valueOf(path1)))) {
                throw new FileNotFoundException();
            }
            Files.createDirectory(Path.of(String.valueOf(path1)));


            out.writeUTF("Directory " + path + " create");

        } catch (IOException e) {
            try {
                out.writeUTF("Ошибка, такая директория уже существует");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    //    метод показать все файлы каталога
    private void getFileList() {
        String p = String.join(" " + " ", new File(currentPath.toString()).list());
        try {
            out.writeUTF(p);
        } catch (IOException exception) {
            exception.printStackTrace();
        }

    }


    public String getNickname() {
        return nickname;
    }

    public String getLogin() {
        return login;
    }

    private void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
