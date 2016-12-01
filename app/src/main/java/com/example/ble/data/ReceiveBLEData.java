package com.example.ble.data;


import android.util.Log;

public class ReceiveBLEData {
	private byte[] PrimitiveArray;// 接收到的源数组
	private byte[] UsefulData;// 接收返回包数组，只解析数据部分，例如秤 解析第八位到校验和前一位的数据部分
	private int receiveNowStates;// 接收到当前返回状态信息-->例如 秤 5101 为设置用户状态
	private int mPaserIndex;
	private int mDataLength;

	/**
	 * 初始化接收数组以及各参数
	 */
	public ReceiveBLEData() {
		mPaserIndex = 0;
		receiveNowStates = 0;
		mDataLength = 0;
		PrimitiveArray = new byte[256];
	}

	/**
	 * 返回当前状态
	 * 
	 * @return
	 */
	public int getReceiveNowStates() {
		return receiveNowStates;
	}

	/**
	 * 清空数据数组以及接收到的返回状态
	 */
	public void clearResponseBytes() {
		UsefulData = null;
		receiveNowStates = 0;
	}

	/**
	 * 获取数据数组
	 * 
	 * @return 只包含数据数组
	 */
	public byte[] getData() {
		return UsefulData;
	}

	/**
	 * 每次接收到广播消息之后将数组拼接到源数组PrimitiveArray中
	 * 
	 * @param bdata
	 */
	public void AppendStream(byte[] bdata) {
		if (bdata == null || bdata.length < 1)
			return;
		if (PrimitiveArray != null
				&& mDataLength + bdata.length >= PrimitiveArray.length) {
			mDataLength = 0;
			mPaserIndex = 0;
			if (PrimitiveArray.length <= bdata.length)
				return;
		}
		if (PrimitiveArray == null || mDataLength == 0
				|| PrimitiveArray[0] != 0x68
				|| (mDataLength > 1 && (PrimitiveArray[1] & 0x80) == 0)) {
			if (bdata[0] != 0x68) {
				Log.e("AppendStream", "error  bdata");
				return;
			}
			mPaserIndex = 0;
			mDataLength = bdata.length;
			System.arraycopy(bdata, 0, PrimitiveArray, 0, bdata.length);
		} else {
			System.arraycopy(bdata, 0, PrimitiveArray, mDataLength,
					bdata.length);
			mDataLength += bdata.length;

		}
	}

	/**
	 * 判断数据是否可用
	 * 
	 * @return
	 */
	@SuppressWarnings("unused")
	public boolean DataAvailable() {
		int i, l, index;
		int sum = 0;
		byte[] bytes = PrimitiveArray;

		if (UsefulData != null)
			return true;
		if (bytes == null || mDataLength < 9 + mPaserIndex)
			return false;
		if (bytes[mPaserIndex] != 0x68) {
			mPaserIndex = 0;
			mDataLength = 0;
			return false;
		}
		index = mPaserIndex;
		l = (((int) bytes[index + 3]) & 0xFF)
				+ ((((int) bytes[index + 4]) & 0xFF) << 8);
		if (l > 1 && mDataLength >= (l + 7 + index) && (l + 7) >= 9
				&& bytes[index] == 0x68 && bytes[index + 5 + l + 1] == 0x16) {// 判断是否是一个完整回包
			for (i = 0; i < 5 + l; i++)
				sum += (((int) bytes[i + index]) & 0xFF);// 计算校验和
			if ((byte) (sum & 0xFF) != bytes[index + 5 + l])// 校验和错误
				Log.e("isReadyData", "sum error ");
			if (true || (byte) (sum & 0xFF) == bytes[index + 5 + l]) {// 校验和正确
				receiveNowStates = (((int) bytes[index + 5]) & 0xFF)// 解析第五位到第六位即为当前状态
						+ ((((int) bytes[index + 6]) & 0xFF) << 8);
				UsefulData = new byte[l + 7 - 9];// 创建数据数组
				for (i = 0; i < (l + 7 - 9); i++) {
					UsefulData[i] = bytes[index + i + 7];// 解析数据部分，
															// 解析第八位到校验和前一位的数据部分
				}
				index += (5 + l + 2);
				if (index >= mDataLength) {
					mDataLength = 0;
					mPaserIndex = 0;
				} else
					mPaserIndex = index;
				return true;
			} else {
				mDataLength = 0;
				mPaserIndex = 0;
			}
		} else {// 如果回包错误，则直接丢弃，返回false
			if (l < 2
					|| (mDataLength >= (l + 7 + index) && bytes[index + 5 + l
							+ 1] != 0x16)) {
				mDataLength = 0;
				mPaserIndex = 0;
			}
		}
		return false;
	}

}
