// 文件名：SimpleContract.sol
pragma solidity ^0.8.1;

contract SimpleContract {
    uint256 public value;

    // 设置 value
    function setValue(uint256 _value) public {
        value = _value;
    }

    // 获取 value
    function getValue() public view returns (uint256) {
        return value;
    }
}