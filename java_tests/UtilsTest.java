package com.drscbt.shared.utils;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class UtilsTest {
    private ArrayList<Pair<byte[], int[]>> _arrIntsArrBytes;

    public UtilsTest() {
        this._arrIntsArrBytes = new ArrayList<>();
        this._arrIntsArrBytes.add(Pair.of(
            new byte[]{0,1,2,3, 4,5,6,7, 8,9,10,11, 12,13,14,15},
            new int[]{0x00010203,0x04050607,0x08090A0B,0x0C0D0E0F}
        ));
        this._arrIntsArrBytes.add(Pair.of(
            new byte[]{0,0,0,0,-1,-1,-1,-1},
            new int[]{0x00000000,0xFFFFFFFF}
        ));
    }

    @Test
    public void toUnsignedInt(){
        ArrayList<Pair<Byte, Integer>> bytesInts = new ArrayList<Pair<Byte, Integer>>();
        bytesInts.add(Pair.of((byte)0,0));
        bytesInts.add(Pair.of((byte)-1,255));
        bytesInts.add(Pair.of((byte)-2,254));
        bytesInts.add(Pair.of((byte)127,127));
        bytesInts.add(Pair.of((byte)-127,129));

        for (Pair<Byte, Integer> p : bytesInts) {
            int actual = Utils.toUnsignedInt(p.getLeft());
            assertEquals((int)p.getRight(), actual);
        }
    }

    @Test
    public void maxMin(){
        assertEquals(3, Utils.max(1,2,3));
        assertEquals(3, Utils.max(1,3,2));
        assertEquals(3, Utils.max(2,1,3));
        assertEquals(3, Utils.max(2,3,1));
        assertEquals(3, Utils.max(3,2,1));
        assertEquals(3, Utils.max(3,1,2));

        assertEquals(1, Utils.min(1,2,3));
        assertEquals(1, Utils.min(1,3,2));
        assertEquals(1, Utils.min(2,1,3));
        assertEquals(1, Utils.min(2,3,1));
        assertEquals(1, Utils.min(3,2,1));
        assertEquals(1, Utils.min(3,1,2));
    }

    @Test
    public void modPow() {
        int[][] tests = {
            {1 + 0xFF,     217, (1 << 23) - 15,  3379656},
            {1 + 0xFF,     260, (1 << 23) - 15,  2883876},
            {1 + 0xFF, 1000000, (1 << 23) - 15,  2491284},
            {1 + 0xFF,     217, (1 << 24) -  3,  8627555},
            {1 + 0xFF,     260, (1 << 24) -  3, 13913083},
            {1 + 0xFF, 1000000, (1 << 24) -  3, 10106802},
        };

        for (int[] t : tests) {
            int num = t[0];
            int power = t[1];
            int modulus = t[2];
            int resultExpected = t[3];
            int resultJ = BigInteger.valueOf(num).modPow(BigInteger.valueOf(power), BigInteger.valueOf(modulus)).intValue();
            int resultNa = Utils.modPowNaCall(num, power, modulus);
            assertEquals(resultExpected, resultJ);
            assertEquals(resultExpected, resultNa);
        }
    }

    @Test
    public void md5Int() {
        int[] inpInts = new int[]{0x01020304, 0x05060708, 0xF1F2F3F4, 0xF5F6F7F8};
        byte[] expectedBytes = new byte[]{-0x21,  0x3e, -0x33, -0x57,  0x4c, -0x1e,  0x2b, -0x49, -0x6d,  0x32, -0x7d, -0x76, -0x75,  0x28, -0x1c, -0x18,};
        byte[] resultBytes = Utils.md5(inpInts);
        assertArrayEquals(expectedBytes, resultBytes);
    }

    @Test
    public void intArrToByteArrQuarter() {
        int[] in = new int[]{0x10_20_30_40, 0x11_21_31_41, 0x12_22_32_42};
        byte[] out;
        byte[] exp;

        out = Utils.intArrToByteArrQuarter(in, 0);
        exp = new byte[]{0x10, 0x11, 0x12};
        assertArrayEquals(exp, out);

        out = Utils.intArrToByteArrQuarter(in, 3);
        exp = new byte[]{0x40, 0x41, 0x42};
        assertArrayEquals(exp, out);
    }

    @Test
    public void getPathExt() {
        assertNull(Utils.getPathExt(null));
        assertNull(Utils.getPathExt("data"));
        assertNull(Utils.getPathExt("data."));
        assertNull(Utils.getPathExt(""));
        assertEquals("ext", Utils.getPathExt("data.ext"));
        assertEquals("ext", Utils.getPathExt(".ext"));
    }

    @Test
    public void splitExt() {
        assertNull(Utils.splitExt(null));
        assertEquals(new Utils.SplitExt("data", null), Utils.splitExt("data"));
        assertEquals(new Utils.SplitExt("data", null), Utils.splitExt("data."));
        assertNull(Utils.splitExt(""));
        assertEquals(new Utils.SplitExt("data", "ext"), Utils.splitExt("data.ext"));
        assertEquals(new Utils.SplitExt(null, "ext"), Utils.splitExt(".ext"));
    }

    @Test
    public void readBlockTest() {
        byte[] back = new byte[]{1, 2, 3, 4};
        ByteArrayInputStream bai = new ByteArrayInputStream(back);
        byte[] out = new byte[10];
        Utils.readBlock(bai, 3, out);
        for (int i = 0; i < out.length; i++) {
            if (i < 3) {
                assertEquals(back[i], out[i]);
            } else {
                assertEquals(0, out[i]);
            }
        }
    }

    @Test
    public void fourBytesToInt() {
        assertEquals(
            0,
            Utils.fourBytesToInt((byte)0,(byte)0,(byte)0,(byte)0)
        );
        assertEquals(
            0xFFFFFFFF,
            Utils.fourBytesToInt((byte)-1,(byte)-1,(byte)-1,(byte)-1)
        );
        assertEquals(
            0x01020304,
            Utils.fourBytesToInt((byte)1, (byte)2,(byte)3,(byte)4)
        );
        assertEquals(
            0x80808080,
            Utils.fourBytesToInt((byte)-128,(byte)-128,(byte)-128,(byte)-128)
        );
    }

    @Test
    public void fourIntsToInt() {
        assertEquals(
            0,
            Utils.fourIntsToInt((int)0, (int)0, (int)0, (int)0)
        );
        assertEquals(
            0xFFFFFFFF,
            Utils.fourIntsToInt((int)255, (int)255, (int)255, (int)255)
        );
        assertEquals(
            0x01020304,
            Utils.fourIntsToInt((int)1, (int)2, (int)3, (int)4)
        );
    }

    @Test
    public void byteArrToIntArr(){
        try {
            Utils.byteArrToIntArrPack(new byte[]{1, 2, 3, 4, 5, 6, 7});
            fail("non-divisibe by 4 exception is not thrown");
        } catch (IllegalArgumentException e) {}

        for (Pair<byte[],int[]> t : this._arrIntsArrBytes) {
            byte[] bytesArr = t.getLeft();
            int[] intsArrExp = t.getRight();
            int[] intArrFactual = Utils.byteArrToIntArrPack(bytesArr);
            assertArrayEquals(intsArrExp, intArrFactual);
        }
    }

    @Test
    public void intArrToByteArr(){
        for (Pair<byte[],int[]> t : this._arrIntsArrBytes) {
            byte[] bytesArrExp = t.getLeft();
            int[] intsArr = t.getRight();
            byte[] bytesArrFactual = Utils.intArrToByteArr(intsArr);
            assertArrayEquals(bytesArrExp, bytesArrFactual);
        }
    }

    @Test
    public void distance() {
        ArrayList<DistanceTestPair> tests = new ArrayList<>();
        tests.add(new DistanceTestPair("abcde", "abcde"  , 0));
        tests.add(new DistanceTestPair("abcde", "abXde"  , 1));
        tests.add(new DistanceTestPair("abcde", "abcd"   , 1));
        tests.add(new DistanceTestPair("abcde", "bcde"   , 1));
        tests.add(new DistanceTestPair("abcde", "abde"   , 1));
        tests.add(new DistanceTestPair("abcde", "Xabcde" , 1));
        tests.add(new DistanceTestPair("abcde", "abcdeX" , 1));
        tests.add(new DistanceTestPair("abcde", "XabcdeX", 2));
        tests.add(new DistanceTestPair("abcde", "XabZdeX", 3));
        tests.add(new DistanceTestPair("abcde", "abXcde" , 1));
        tests.add(new DistanceTestPair("abcde", "abXcd"  , 2));

        for (DistanceTestPair test : tests) {
            int d1 = Utils.distance(test.s1, test.s2);
            int d2 = Utils.distance(test.s2, test.s1);
            assertEquals(test.distance, d1);
            assertEquals(d2, d1);
        }
    }

    static class DistanceTestPair {
        String s1;
        String s2;
        int distance;

        public DistanceTestPair(String s1, String s2, int distance) {
            this.s1 = s1;
            this.s2 = s2;
            this.distance = distance;
        }
    }
}
