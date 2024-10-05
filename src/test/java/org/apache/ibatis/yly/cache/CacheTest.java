package org.apache.ibatis.yly.cache;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Reader;

public class CacheTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    public void setUp() throws Exception {
        // create a SqlSessionFactory
        try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/yly/cache/mybatis-config.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }

        // populate in-memory database
        BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
                "org/apache/ibatis/yly/cache/CreateDB.sql");
    }

    /**
     * 测试一级缓存
     */
    @Test
    public void testCache() throws Exception {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            PersonMapper personMapper = session.getMapper(PersonMapper.class);

            // 第一次查询
            Person person1 = personMapper.findById(1);
            System.out.println("1 -- : " + person1);
            
            // 第二次查询
            Person person2 = personMapper.findById(1);
            System.out.println("2 -- : " + person2);

            // 输出对象的 hashCode，检查是否为同一个对象
            System.out.println("1 -- : " + person1.hashCode());
            System.out.println("2 -- : " + person2.hashCode());
            

        } finally {
            session.close();
        }
    }

    /**
     * 测试二级缓存
     */
    @Test
    public void testSecondLevelCache() throws Exception {
        //第一次查询 Start
        SqlSession session = sqlSessionFactory.openSession();
        PersonMapper personMapper = session.getMapper(PersonMapper.class);
        Person person1 = personMapper.findById(1);
        System.out.println("1 -- : " + person1);
        session.commit();//注意, 这里需要commit操作, 会清空一级缓存并添加二级缓存
        //第一次查询 End

        //第二次查询, 使用同一个SqlSession进行查询 Start
        Person person2 = personMapper.findById(1);
        System.out.println("2 -- : " + person2);
        session.commit();//注意, 这里需要commit操作, 会清空一级缓存并添加二级缓存
        //第二次查询, 使用同一个SqlSession进行查询 End

        //第三次查询, 重新创建一个新的SqlSession查询, 依然能够从缓存中获取数据 Start
        SqlSession session1 = sqlSessionFactory.openSession();
        PersonMapper personMapper1 = session1.getMapper(PersonMapper.class);
        Person person3 = personMapper.findById(1);
        System.out.println("3 -- : " + person3);
        //第三次查询, 重新创建一个新的SqlSession查询, 依然能够从缓存中获取数据 End
    }
}
