package sdfs.server;

import sdfs.*;
import sdfs.protocol.Invocation;
import sdfs.protocol.Response;
import sdfs.protocol.Url;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;


public abstract class AbstractServer implements Server {
    private String host;
    private int port;
    private Class service;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public abstract void listenRequest();

    @Override
    public void register(String host, int port, Class service) {
        if (host == null || host.equals(""))
            host = Constants.DEFAULT_HOST;
        if (port < 0)
            port = Constants.DEFAULT_PORT;
        if (service == null)
            return;
        this.host = host;
        this.port = port;
        this.service = service;
        Registry.register(new Url(host, port, service.getName()));
    }

    @Override
    public void unRegister() {
        if (host != null && !host.equals("") && port > 0 && service != null) {
            Registry.unRegister(new Url(host, port, service.getName()));
        }
    }

    @Override
    public void listenRequest(int port) {
        try (ServerSocket listener = new ServerSocket(port)) {
            while (true) {
                try (Socket socket = listener.accept()) {
//                    new Thread(() -> {
                        try {
                            Response response = new Response();
                            //将请求反序列化
                            ObjectInputStream objectInputStream = null;
                            objectInputStream = new ObjectInputStream(socket.getInputStream());
                            Object object = null;
                            try {
                                object = objectInputStream.readObject();
                                //调用服务
                                if (object instanceof Invocation) {
                                    //利用反射机制调用对应的方法
                                    Invocation invocation = (Invocation) object;
                                    Method method = getClass().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
                                    response.setReturnType(method.getReturnType());
                                    response.setReturnValue(method.invoke(this, invocation.getArguments()));
                                } else {
                                    throw new UnsupportedOperationException();
                                }
                            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                                response.setException(e);
                            } finally {
                                //返回结果
                                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                                objectOutputStream.writeObject(response);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
//                    }).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            unRegister(); //关闭服务器后，将注册的服务移除
        }
    }
}