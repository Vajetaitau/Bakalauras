package dao.impl;

import dao.PacketDaoCustom;
import dto.PacketsInfo;
import entities.Packet;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.jdbc.Work;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class PacketDaoImpl implements PacketDaoCustom {

    @Autowired
    EntityManager entityManager;

    @Autowired
    private SessionFactory sessionFactory;

    @Override
    public void insertPackets(final List<Packet> packets) {
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        //get Connction from Session
        session.doWork(new Work() {
            @Override
            public void execute(Connection conn) throws SQLException {
                PreparedStatement pstmt = null;
                try {
                    String sqlInsert = "insert into packets values (?, ?, ?, ?, ?, ?, ?, ?, ?) ";
                    pstmt = conn.prepareStatement(sqlInsert);
                    for (Packet p : packets) {
                        pstmt.setString(1, p.getDestination());
                        pstmt.setString(2, p.getFileName());
                        pstmt.setString(3, p.getInfo());
                        pstmt.setInt(4, p.getLength());
                        pstmt.setInt(5, p.getNumber());
                        pstmt.setString(6, p.getProtocol());
                        pstmt.setString(7, p.getSource());
                        pstmt.setTimestamp(8, p.getTimestamp());
                        pstmt.setInt(9, p.getId());

                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    pstmt.executeBatch();
                } finally {
                    pstmt.close();
                }
            }
        });
        tx.commit();
        session.close();
    }

    @Override
    public List<PacketsInfo> findPacketCounts(Timestamp start, Timestamp end, Integer increment) {
        StringBuilder query = new StringBuilder()
                .append("select tt.interval_start as t, destination as d, count(*) as c, sum(count(*)) over (partition by tt.interval_start order by tt.interval_start asc) ")
                .append("from packets ")
                .append("right join ( ")
                .append("select generate_series as interval_start, generate_series + cast(concat(:increment, 'second') as interval) as interval_end ")
                .append("from generate_series(cast(:start as timestamp), :end, cast(concat(:increment, 'second') as interval))")
                .append(") as tt on packets.timestamp >= tt.interval_start and packets.timestamp <= tt.interval_end ")
                .append("where filename = '0' ")
                .append("group by tt.interval_start, destination ")
                .append("order by tt.interval_start asc");

        Query nativeQuery = entityManager.createNativeQuery(query.toString());
        nativeQuery.setParameter("start", start);
        nativeQuery.setParameter("end", end);
        nativeQuery.setParameter("increment", increment);

        List<PacketsInfo> list = new ArrayList<PacketsInfo>();
        for (Object r : nativeQuery.getResultList()) {
            Object[] resultRow = (Object[]) r;
            Timestamp intervalStart = (Timestamp) resultRow[0];
            String destination = (String) resultRow[1];
            long count = ((BigInteger) resultRow[2]).longValue();
            long sum = ((BigDecimal) resultRow[3]).longValue();
            PacketsInfo packetCount = new PacketsInfo(null, destination, intervalStart, count, sum);
            list.add(packetCount);
        }
        return list;
    }

    public void flushEntityManager() {
        entityManager.flush();
    }
}
