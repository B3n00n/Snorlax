import struct


class PacketWriter:
    def __init__(self):
        self.data = bytearray()
    
    def write_u8(self, value: int):
        self.data.extend(struct.pack('B', value & 0xFF))
    
    def write_u16(self, value: int):
        self.data.extend(struct.pack('>H', value & 0xFFFF))
    
    def write_u32(self, value: int):
        self.data.extend(struct.pack('>I', value & 0xFFFFFFFF))
    
    def write_string(self, value: str):
        encoded = value.encode('utf-8')
        self.write_u32(len(encoded))
        self.data.extend(encoded)
    
    def write_ascii_string(self, value: str):
        encoded = value.encode('ascii')
        self.write_u16(len(encoded))
        self.data.extend(encoded)
    
    def to_bytes(self) -> bytes:
        return bytes(self.data)


class PacketReader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0
    
    def read_u8(self) -> int:
        if self.offset + 1 > len(self.data):
            raise ValueError("Not enough data")
        value = struct.unpack_from('B', self.data, self.offset)[0]
        self.offset += 1
        return value
    
    def read_u16(self) -> int:
        if self.offset + 2 > len(self.data):
            raise ValueError("Not enough data")
        value = struct.unpack_from('>H', self.data, self.offset)[0]
        self.offset += 2
        return value
    
    def read_u32(self) -> int:
        if self.offset + 4 > len(self.data):
            raise ValueError("Not enough data")
        value = struct.unpack_from('>I', self.data, self.offset)[0]
        self.offset += 4
        return value
    
    def read_string(self) -> str:
        length = self.read_u32()
        if self.offset + length > len(self.data):
            raise ValueError("Not enough data")
        value = self.data[self.offset:self.offset + length].decode('utf-8')
        self.offset += length
        return value
    
    def read_ascii_string(self) -> str:
        length = self.read_u16()
        if self.offset + length > len(self.data):
            raise ValueError("Not enough data")
        value = self.data[self.offset:self.offset + length].decode('ascii')
        self.offset += length
        return value