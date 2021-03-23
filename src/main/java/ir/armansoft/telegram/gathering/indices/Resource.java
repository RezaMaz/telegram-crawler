package ir.armansoft.telegram.gathering.indices;

import java.io.Serializable;

public interface Resource<PK extends Serializable> {
    PK getId();

    String getIndex();
}
