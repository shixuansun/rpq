// automatically generated by the FlatBuffers compiler, do not modify

package ddlog.__tc;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class Edge extends Table {
  public static Edge getRootAsEdge(ByteBuffer _bb) { return getRootAsEdge(_bb, new Edge()); }
  public static Edge getRootAsEdge(ByteBuffer _bb, Edge obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; vtable_start = bb_pos - bb.getInt(bb_pos); vtable_size = bb.getShort(vtable_start); }
  public Edge __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public long s() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public long t() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }

  public static int createEdge(FlatBufferBuilder builder,
      long s,
      long t) {
    builder.startObject(2);
    Edge.addT(builder, t);
    Edge.addS(builder, s);
    return Edge.endEdge(builder);
  }

  public static void startEdge(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addS(FlatBufferBuilder builder, long s) { builder.addLong(0, s, 0L); }
  public static void addT(FlatBufferBuilder builder, long t) { builder.addLong(1, t, 0L); }
  public static int endEdge(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}
