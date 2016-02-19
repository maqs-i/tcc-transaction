package org.mengyun.tcctransaction.spring.repository;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.utils.SerializationUtils;

import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by changming.xie on 2/18/16.
 */
public class ZooKeeperTransactionRepository extends CachableTransactionRepository {

    private String zkServers;

    private int zkTimeout;

    private String zkRootPath = "/tcc";

    private volatile ZooKeeper zk;

    public ZooKeeperTransactionRepository() {
        super();
    }

    public void setZkRootPath(String zkRootPath) {
        this.zkRootPath = zkRootPath;
    }

    public void setZkServers(String zkServers) {
        this.zkServers = zkServers;
    }

    public void setZkTimeout(int zkTimeout) {
        this.zkTimeout = zkTimeout;
    }

    @Override
    protected void doCreate(Transaction transaction) {

        try {


            getZk().create(getTxidPath(transaction.getXid()),
                    SerializationUtils.serialize(transaction), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }

    @Override
    protected void doUpdate(Transaction transaction) {

        try {
            Stat stat = getZk().setData(getTxidPath(transaction.getXid()), SerializationUtils.serialize(transaction), transaction.getVersion());
            transaction.setVersion(stat.getVersion());
        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }

    @Override
    protected void doDelete(Transaction transaction) {
        try {
            getZk().delete(getTxidPath(transaction.getXid()), transaction.getVersion());
        } catch (Exception e) {
            throw new TransactionIOException(e);
        }
    }

    @Override
    protected List<Transaction> doFindAll(List<TransactionXid> xids) {

        List<Transaction> transactions = new ArrayList<Transaction>();

        for (Xid xid : xids) {
            byte[] content = null;
            try {
                Stat stat = new Stat();
                content = getZk().getData(getTxidPath(xid), false, stat);
                Transaction transaction = (Transaction) SerializationUtils.deserialize(content);
                transaction.setVersion(stat.getVersion());
                transactions.add(transaction);
            } catch (KeeperException.NoNodeException e) {

            } catch (Exception e) {
                throw new TransactionIOException(e);
            }
        }
        return transactions;
    }

    @Override
    protected List<Transaction> doFindAll() {

        List<Transaction> transactions = new ArrayList<Transaction>();

        List<String> znodePaths = null;
        try {
            znodePaths = getZk().getChildren(zkRootPath, false);
        } catch (Exception e) {
            throw new TransactionIOException(e);
        }

        for (String znodePath : znodePaths) {

            byte[] content = null;
            try {
                Stat stat = new Stat();
                content = getZk().getData(getTxidPath(znodePath), false, stat);
                Transaction transaction = (Transaction) SerializationUtils.deserialize(content);
                transaction.setVersion(stat.getVersion());
                transactions.add(transaction);
            } catch (Exception e) {
                throw new TransactionIOException(e);
            }
        }

        return transactions;
    }

    private ZooKeeper getZk() {

        if (zk == null) {
            synchronized (ZooKeeperTransactionRepository.class) {
                if (zk == null) {
                    try {
                        zk = new ZooKeeper(zkServers, zkTimeout, new Watcher() {
                            @Override
                            public void process(WatchedEvent watchedEvent) {

                            }
                        });

                        Stat stat = zk.exists(zkRootPath, false);

                        if (stat == null) {
                            zk.create(zkRootPath, zkRootPath.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                        }
                    } catch (Exception e) {
                        throw new TransactionIOException(e);
                    }
                }
            }
        }
        return zk;
    }

    private String getTxidPath(Xid xid) {
        return String.format("%s/%s", zkRootPath, xid);
    }

    private String getTxidPath(String znodePath) {
        return String.format("%s/%s", zkRootPath, znodePath);
    }


}
